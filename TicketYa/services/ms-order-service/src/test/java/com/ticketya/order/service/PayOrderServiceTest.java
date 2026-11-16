package com.ticketya.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketya.order.client.InventoryPort;
import com.ticketya.order.client.PaymentPort;
import com.ticketya.order.client.PaymentPort.PaymentInfo;
import com.ticketya.order.client.TicketPort;
import com.ticketya.order.client.TicketPort.TicketInfo;
import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.dto.request.PayOrderRequest;
import com.ticketya.order.entity.OrderItem;
import com.ticketya.order.entity.PayAttempt;
import com.ticketya.order.entity.PayAttemptResult;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.mapper.OrderMapper;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.order.repository.PayAttemptRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.math.BigDecimal;
import java.time.Clock;
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

/** Orquestación de la saga con colaboradores simulados: qué se llama (y qué no) en cada desenlace. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayOrderServiceTest {

    @Mock PayOrderTransactions txOps;
    @Mock OrderRepository orderRepo;
    @Mock PayAttemptRepository attemptRepo;
    @Mock PaymentPort payments;
    @Mock InventoryPort inventory;
    @Mock TicketPort tickets;
    @Mock HoldReleaser holdReleaser;

    PayOrderService service;
    PurchaseOrder order;
    Identity caller;
    final PayOrderRequest req = new PayOrderRequest(PayOrderRequest.PaymentMethod.CARD, "tok_approved", "4242");
    final Instant now = Instant.parse("2026-11-08T12:00:00Z");

    @BeforeEach
    void setUp() {
        OrderProperties props = new OrderProperties(6, 600, new OrderProperties.Retry(2, java.time.Duration.ofMillis(1)),
                new OrderProperties.Jobs(new OrderProperties.Expire(true, 1000, 10),
                        new OrderProperties.Reconcile(true, 1000, java.time.Duration.ZERO, java.time.Duration.ofMinutes(2))),
                new OrderProperties.Refunds(72, 5, java.time.Duration.ofSeconds(5), java.time.Duration.ofMinutes(5),
                        java.time.Duration.ofSeconds(60), true, 5000, 20),
                new OrderProperties.Cancellation(true, 15000, 100));
        service = new PayOrderService(txOps, orderRepo, attemptRepo, payments, inventory, tickets, holdReleaser,
                new RetryExecutor(props), new OrderMapper(), props, Clock.fixed(now, ZoneOffset.UTC));

        UUID customer = UUID.randomUUID();
        caller = new Identity(customer, Role.CUSTOMER);
        order = PurchaseOrder.create(UUID.randomUUID(), customer, UUID.randomUUID(), now.plusSeconds(9999), "ARS",
                UUID.randomUUID(), now.plusSeconds(600), "k", "h",
                List.of(new OrderItem.Snapshot(UUID.randomUUID(), UUID.randomUUID(), "A-1", new BigDecimal("100.00"), "ARS")),
                now);
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(txOps.begin(any(), any(), anyString()))
                .thenReturn(new PayOrderTransactions.Start(PayOrderTransactions.StartKind.PROCEED, null, null));
    }

    private PaymentInfo info(String status) {
        return new PaymentInfo(UUID.randomUUID(), order.getId(), status, "CARD", "4242");
    }

    private TicketInfo ticket() {
        OrderItem item = order.getItems().get(0);
        return new TicketInfo(UUID.randomUUID(), item.getId(), order.getEventId(), item.getSectorId(), item.getSeatId(),
                "A-1", "qr", "VALID", now);
    }

    @Test
    void pago_aprobado_confirma_y_devuelve_entradas() {
        when(payments.charge(any())).thenReturn(info("APPROVED"));
        when(tickets.issue(any(), any(), any(), any())).thenReturn(List.of(ticket()));

        var response = service.pay(caller, order.getId(), "idem-1", req);

        assertThat(response.tickets()).hasSize(1);
        verify(inventory).confirmHold(order.getHoldId());
        verify(txOps).completeConfirmed(order.getId());
    }

    @Test
    void pago_rechazado_libera_el_hold_y_responde_402() {
        when(payments.charge(any())).thenReturn(info("REJECTED"));
        when(txOps.recordRejected(order.getId())).thenReturn(order.getHoldId());

        assertThatThrownBy(() -> service.pay(caller, order.getId(), "idem-1", req))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("PAYMENT_REJECTED"));

        verify(holdReleaser).releaseQuietly(order.getHoldId());
        verify(inventory, never()).confirmHold(any());
    }

    @Test
    void hold_vencido_al_confirmar_reintegra_y_responde_410() {
        when(payments.charge(any())).thenReturn(info("APPROVED"));
        org.mockito.Mockito.doThrow(OrderException.reservaVencida("vencido")).when(inventory).confirmHold(any());
        order.registrarPagoAprobado(UUID.randomUUID(), "CARD", "4242");

        assertThatThrownBy(() -> service.pay(caller, order.getId(), "idem-1", req))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("HOLD_EXPIRED"));

        verify(txOps).iniciarCompensacion(order.getId(), PayAttempt.REASON_HOLD_EXPIRED);
        verify(payments).refund(eq(order.getPaymentId()), eq(order.getTotal()), anyString(), eq("refund-" + order.getId()));
        verify(inventory, never()).releaseSold(any(), any(), any());      // nunca se vendió
        verify(txOps).finalizarCompensacion(order.getId());
        verify(tickets, never()).issue(any(), any(), any(), any());
    }

    @Test
    void falla_la_emision_reintegra_y_devuelve_los_lugares_vendidos() {
        when(payments.charge(any())).thenReturn(info("APPROVED"));
        when(tickets.issue(any(), any(), any(), any())).thenThrow(OrderException.servicioNoDisponible("caído"));
        order.registrarPagoAprobado(UUID.randomUUID(), "CARD", "4242");
        when(attemptRepo.findByOrderIdAndResultIn(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.pay(caller, order.getId(), "idem-1", req))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("UPSTREAM_UNAVAILABLE"));

        verify(txOps).iniciarCompensacion(order.getId(), PayAttempt.REASON_TICKETS_FAILED);
        verify(payments).refund(any(), any(), anyString(), anyString());
        verify(txOps, never()).completeConfirmed(any());
    }

    @Test
    void inventory_caido_no_compensa_queda_para_la_reconciliacion() {
        when(payments.charge(any())).thenReturn(info("APPROVED"));
        org.mockito.Mockito.doThrow(OrderException.servicioNoDisponible("caído")).when(inventory).confirmHold(any());

        assertThatThrownBy(() -> service.pay(caller, order.getId(), "idem-1", req)).isInstanceOf(OrderException.class);

        verify(txOps, never()).iniciarCompensacion(any(), anyString());
        verify(payments, never()).refund(any(), any(), anyString(), anyString());
    }

    @Test
    void reintento_de_un_pago_ya_confirmado_no_cobra_de_nuevo() {
        when(txOps.begin(any(), any(), anyString()))
                .thenReturn(new PayOrderTransactions.Start(PayOrderTransactions.StartKind.REPLAY_CONFIRMED, null, null));
        when(tickets.issue(any(), any(), any(), any())).thenReturn(List.of(ticket()));

        var response = service.pay(caller, order.getId(), "idem-1", req);

        assertThat(response.tickets()).hasSize(1);
        verify(payments, never()).charge(any());
    }

    @Test
    void sin_idempotency_key_es_400() {
        assertThatThrownBy(() -> service.pay(caller, order.getId(), " ", req)).isInstanceOf(OrderException.class);
        verify(payments, never()).charge(any());
    }

    @Test
    void reanudar_un_intento_pendiente_sin_rastro_en_payment_no_cobra() {
        PayAttempt attempt = PayAttempt.start(order.getId(), order.getId() + ":k", now.minusSeconds(10));
        when(attemptRepo.findByOrderIdAndResultIn(eq(order.getId()), any())).thenReturn(List.of(attempt));
        when(payments.findByOrder(order.getId())).thenReturn(List.of());

        service.resume(order.getId());

        verify(payments, never()).charge(any());
        verify(txOps, never()).abandon(any());                 // todavía dentro del plazo de gracia
        assertThat(attempt.getResult()).isEqualTo(PayAttemptResult.PENDING);
    }
}
