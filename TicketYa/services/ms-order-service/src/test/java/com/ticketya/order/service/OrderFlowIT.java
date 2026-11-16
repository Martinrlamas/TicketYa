package com.ticketya.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ticketya.order.AbstractPostgresTest;
import com.ticketya.order.client.CatalogPort.SaleInfo;
import com.ticketya.order.client.CatalogPort.SectorSale;
import com.ticketya.order.client.InventoryPort.HoldInfo;
import com.ticketya.order.client.PaymentPort.PaymentInfo;
import com.ticketya.order.client.TicketPort.TicketInfo;
import com.ticketya.order.dto.request.CreateOrderRequest;
import com.ticketya.order.dto.request.PayOrderRequest;
import com.ticketya.order.entity.OrderStatus;
import com.ticketya.order.exception.OrderException;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Crear / pagar / cancelar / expirar contra PostgreSQL real, con catalog, inventory, payment y ticket simulados. */
class OrderFlowIT extends AbstractPostgresTest {

    @Autowired CreateOrderService createService;
    @Autowired PayOrderService payService;
    @Autowired CancelOrderService cancelService;
    @Autowired OrderQueryService queryService;
    @Autowired ExpirePendingOrdersService expireService;

    UUID eventId;
    UUID sectorId;
    UUID seatId;
    Identity customer;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        sectorId = UUID.randomUUID();
        seatId = UUID.randomUUID();
        customer = new Identity(UUID.randomUUID(), Role.CUSTOMER);

        when(catalog.getSaleInfo(eventId)).thenReturn(new SaleInfo(eventId, "PUBLISHED", true,
                Instant.now().plusSeconds(86400),
                List.of(new SectorSale(sectorId, "Campo", "GENERAL", new BigDecimal("500.00"), "ARS"))));
        when(inventory.createHold(any(), any(), anyInt(), any()))
                .thenAnswer(i -> new HoldInfo(UUID.randomUUID(), Instant.now().plusSeconds(600), Map.of()));
    }

    private CreateOrderRequest request(int quantity) {
        return new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(sectorId, null, quantity)));
    }

    private PayOrderRequest card() {
        return new PayOrderRequest(PayOrderRequest.PaymentMethod.CARD, "tok_approved", "4242");
    }

    private UUID createOrder(int quantity) {
        return createService.create(customer, UUID.randomUUID().toString(), request(quantity)).body().orderId();
    }

    @Test
    void crear_con_la_misma_clave_devuelve_la_misma_orden_y_con_otro_payload_422() {
        var first = createService.create(customer, "k-1", request(2));
        var again = createService.create(customer, "k-1", request(2));

        assertThat(first.created()).isTrue();
        assertThat(again.created()).isFalse();
        assertThat(again.body().orderId()).isEqualTo(first.body().orderId());
        assertThatThrownBy(() -> createService.create(customer, "k-1", request(3)))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void limite_por_cliente_con_10_solicitudes_simultaneas_de_1_entrada_solo_6_prosperan() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger ok = new AtomicInteger();
            AtomicInteger limit = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final String key = "c-" + i;
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        createService.create(customer, key, request(1));
                        ok.incrementAndGet();
                    } catch (OrderException e) {
                        if (e.hasCode("PURCHASE_LIMIT_EXCEEDED")) {
                            limit.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
            assertThat(ok.get()).isEqualTo(6);
            assertThat(limit.get()).isEqualTo(4);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void pago_aprobado_confirma_la_orden() {
        UUID orderId = createOrder(1);
        when(payments.charge(any())).thenReturn(new PaymentInfo(UUID.randomUUID(), orderId, "APPROVED", "CARD", "4242"));
        when(tickets.issue(any(), any(), any(), any())).thenAnswer(i -> {
            List<com.ticketya.order.client.TicketPort.TicketItem> items = i.getArgument(3);
            return items.stream().map(it -> new TicketInfo(UUID.randomUUID(), it.orderItemId(), eventId, sectorId,
                    null, "Campo", "qr", "VALID", Instant.now())).toList();
        });

        var response = payService.pay(customer, orderId, "p-1", card());

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.tickets()).hasSize(1);
        // reintento con la misma clave: mismo resultado y un único cobro
        var again = payService.pay(customer, orderId, "p-1", card());
        assertThat(again.status()).isEqualTo(OrderStatus.CONFIRMED);
        org.mockito.Mockito.verify(payments, org.mockito.Mockito.times(1)).charge(any());
    }

    @Test
    void pago_rechazado_deja_la_orden_PAYMENT_REJECTED_y_libera_la_cuota() {
        UUID orderId = createOrder(6);
        when(payments.charge(any())).thenReturn(new PaymentInfo(null, orderId, "REJECTED", "CARD", "4242"));

        assertThatThrownBy(() -> payService.pay(customer, orderId, "p-1", card()))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("PAYMENT_REJECTED"));

        assertThat(queryService.obtenerUno(customer, orderId).status()).isEqualTo(OrderStatus.PAYMENT_REJECTED);
        createOrder(6);                                   // la cuota volvió a 0: se puede comprar de nuevo
    }

    @Test
    void cancelar_libera_la_cuota_y_una_cancelada_no_se_vuelve_a_cancelar() {
        UUID orderId = createOrder(6);
        assertThat(cancelService.cancel(customer, orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        createOrder(6);                                   // la cuota volvió a 0

        assertThatThrownBy(() -> cancelService.cancel(customer, orderId))
                .isInstanceOfSatisfying(OrderException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));
    }

    @Test
    void una_orden_ajena_responde_404_al_consultar_pagar_y_cancelar() {
        UUID orderId = createOrder(1);
        Identity stranger = new Identity(UUID.randomUUID(), Role.CUSTOMER);

        assertThatThrownBy(() -> queryService.obtenerUno(stranger, orderId))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
        assertThatThrownBy(() -> payService.pay(stranger, orderId, "p-1", card()))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
        assertThatThrownBy(() -> cancelService.cancel(stranger, orderId))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void el_job_expira_las_ordenes_con_la_reserva_vencida_y_libera_la_cuota() {
        UUID orderId = createOrder(6);
        jdbc.update("update purchase_order set hold_expires_at = now() - interval '1 minute' where id = ?", orderId);

        assertThat(expireService.expirarLote()).isGreaterThanOrEqualTo(1);

        assertThat(queryService.obtenerUno(customer, orderId).status()).isEqualTo(OrderStatus.EXPIRED);
        createOrder(6);                                   // cuota liberada
        assertThatThrownBy(() -> payService.pay(customer, orderId, "p-1", card()))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("HOLD_EXPIRED"));
    }

    @Test
    void pagar_una_orden_con_la_reserva_vencida_responde_410_sin_cobrar() {
        UUID orderId = createOrder(1);
        jdbc.update("update purchase_order set hold_expires_at = now() - interval '1 minute' where id = ?", orderId);

        assertThatThrownBy(() -> payService.pay(customer, orderId, "p-1", card()))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("HOLD_EXPIRED"));

        org.mockito.Mockito.verify(payments, org.mockito.Mockito.never()).charge(any());
    }

    @Test
    void falla_la_emision_de_entradas_reintegra_y_deja_FAILED_REFUNDED() {
        UUID orderId = createOrder(2);
        when(payments.charge(any())).thenReturn(new PaymentInfo(UUID.randomUUID(), orderId, "APPROVED", "CARD", "4242"));
        when(tickets.issue(any(), any(), any(), any())).thenThrow(OrderException.servicioNoDisponible("ticket caído"));

        assertThatThrownBy(() -> payService.pay(customer, orderId, "p-1", card()))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("UPSTREAM_UNAVAILABLE"));

        assertThat(queryService.obtenerUno(customer, orderId).status()).isEqualTo(OrderStatus.FAILED_REFUNDED);
        org.mockito.Mockito.verify(payments).refund(any(), any(), anyString(), anyString());
        org.mockito.Mockito.verify(inventory).releaseSold(any(), any(), any());
    }
}
