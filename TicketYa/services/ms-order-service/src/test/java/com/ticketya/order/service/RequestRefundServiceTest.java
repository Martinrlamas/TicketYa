package com.ticketya.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.dto.request.CreateRefundRequest;
import com.ticketya.order.entity.OrderItem;
import com.ticketya.order.entity.OrderItemStatus;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.entity.RefundRequest;
import com.ticketya.order.entity.RefundRequestStatus;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.mapper.RefundRequestMapper;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.order.repository.RefundRequestRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Regla de 72 h (justo en el límite, antes y después), duplicados y propiedad. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestRefundServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    @Mock OrderRepository orderRepo;
    @Mock RefundRequestRepository refundRepo;

    RequestRefundService service;
    Identity owner;
    PurchaseOrder order;
    OrderItem item;

    @BeforeEach
    void setUp() {
        OrderProperties props = new OrderProperties(6, 600, null, null,
                new OrderProperties.Refunds(72, 5, Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofSeconds(60), true, 5000, 20),
                null);
        service = new RequestRefundService(orderRepo, refundRepo, new RefundRequestMapper(), props,
                TestTransactions.template(), Clock.fixed(NOW, ZoneOffset.UTC));
        when(refundRepo.saveAndFlush(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));
        owner = new Identity(UUID.randomUUID(), Role.CUSTOMER);
    }

    /** Orden CONFIRMED de {@code owner} cuyo evento empieza dentro de {@code startsIn}. */
    private void orderStartingIn(Duration startsIn) {
        order = PurchaseOrder.create(UUID.randomUUID(), owner.userId(), UUID.randomUUID(), NOW.plus(startsIn), "ARS",
                UUID.randomUUID(), NOW.plusSeconds(600), "k", "h",
                List.of(new OrderItem.Snapshot(UUID.randomUUID(), UUID.randomUUID(), "A-1", new BigDecimal("100.00"), "ARS")),
                NOW.minusSeconds(3600));
        order.registrarPagoAprobado(UUID.randomUUID(), "CARD", "4242");
        order.confirm();
        item = order.getItems().get(0);
        when(orderRepo.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    private void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void cinco_dias_antes_se_crea_PENDING_y_el_item_queda_REFUND_REQUESTED() {
        orderStartingIn(Duration.ofDays(5));
        var response = service.request(owner, order.getId(), item.getId(), new CreateRefundRequest("No puedo asistir"));
        assertThat(response.status()).isEqualTo(RefundRequestStatus.PENDING);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.REFUND_REQUESTED);
    }

    @Test
    void justo_72_horas_antes_todavia_se_acepta() {
        orderStartingIn(Duration.ofHours(72));
        assertThat(service.request(owner, order.getId(), item.getId(), null).status()).isEqualTo(RefundRequestStatus.PENDING);
    }

    @Test
    void un_segundo_despues_del_limite_responde_422_REFUND_WINDOW_CLOSED() {
        orderStartingIn(Duration.ofHours(72).minusSeconds(1));
        assertCode(() -> service.request(owner, order.getId(), item.getId(), null), "REFUND_WINDOW_CLOSED");
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.ACTIVE);
    }

    @Test
    void a_menos_de_72_horas_responde_422() {
        orderStartingIn(Duration.ofHours(10));
        assertCode(() -> service.request(owner, order.getId(), item.getId(), null), "REFUND_WINDOW_CLOSED");
    }

    @Test
    void una_segunda_solicitud_para_el_mismo_item_responde_409() {
        orderStartingIn(Duration.ofDays(5));
        service.request(owner, order.getId(), item.getId(), null);
        assertCode(() -> service.request(owner, order.getId(), item.getId(), null), "INVALID_STATE_TRANSITION");
    }

    @Test
    void un_item_de_otro_cliente_responde_404() {
        orderStartingIn(Duration.ofDays(5));
        Identity stranger = new Identity(UUID.randomUUID(), Role.CUSTOMER);
        assertCode(() -> service.request(stranger, order.getId(), item.getId(), null), "NOT_FOUND");
    }

    @Test
    void un_item_que_no_es_de_la_orden_responde_404() {
        orderStartingIn(Duration.ofDays(5));
        assertCode(() -> service.request(owner, order.getId(), UUID.randomUUID(), null), "NOT_FOUND");
    }

    @Test
    void una_orden_no_confirmada_responde_409() {
        orderStartingIn(Duration.ofDays(5));
        order.cancelarPorEvento();
        assertCode(() -> service.request(owner, order.getId(), item.getId(), null), "INVALID_STATE_TRANSITION");
    }

    @Test
    void solo_un_CUSTOMER_puede_solicitar() {
        orderStartingIn(Duration.ofDays(5));
        Identity admin = new Identity(UUID.randomUUID(), Role.ADMIN);
        assertCode(() -> service.request(admin, order.getId(), item.getId(), null), "FORBIDDEN");
    }
}
