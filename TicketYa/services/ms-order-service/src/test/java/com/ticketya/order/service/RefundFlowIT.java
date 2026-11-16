package com.ticketya.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketya.order.AbstractPostgresTest;
import com.ticketya.order.client.CatalogPort.SaleInfo;
import com.ticketya.order.client.CatalogPort.SectorSale;
import com.ticketya.order.client.InventoryPort.HoldInfo;
import com.ticketya.order.client.PaymentPort.ChargeCommand;
import com.ticketya.order.client.PaymentPort.PaymentInfo;
import com.ticketya.order.client.TicketPort.TicketInfo;
import com.ticketya.order.client.TicketPort.TicketItem;
import com.ticketya.order.dto.request.CreateOrderRequest;
import com.ticketya.order.dto.request.CreateRefundRequest;
import com.ticketya.order.dto.request.PayOrderRequest;
import com.ticketya.order.dto.response.OrderResponse;
import com.ticketya.order.dto.response.RefundRequestResponse;
import com.ticketya.order.entity.OrderItemStatus;
import com.ticketya.order.entity.OrderStatus;
import com.ticketya.order.entity.RefundRequestStatus;
import com.ticketya.order.entity.RefundStep;
import com.ticketya.order.exception.OrderException;
import com.ticketya.platform.error.ApiException;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Devoluciones y cancelación de evento contra PostgreSQL real; catalog, inventory, payment y ticket simulados.
 * Que el lugar devuelto sea recomprable lo garantiza inventory (sold/release, ya probado ahí): acá se verifica
 * que order-service le pida la devolución con el releaseId correcto. El E2E real es del change 07.
 */
class RefundFlowIT extends AbstractPostgresTest {

    @Autowired CreateOrderService createService;
    @Autowired PayOrderService payService;
    @Autowired OrderQueryService queryService;
    @Autowired RequestRefundService requestService;
    @Autowired RefundDecisionService decisionService;
    @Autowired RefundQueryService refundQuery;
    @Autowired RefundRecoveryService recovery;
    @Autowired CancelEventService cancelEventService;

    UUID eventId;
    UUID sectorId;
    Identity customer;
    final Identity admin = new Identity(UUID.randomUUID(), Role.ADMIN);

    static UUID ticketIdOf(UUID orderItemId) {
        return UUID.nameUUIDFromBytes(("ticket:" + orderItemId).getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        sectorId = UUID.randomUUID();
        customer = new Identity(UUID.randomUUID(), Role.CUSTOMER);

        when(catalog.getSaleInfo(eventId)).thenReturn(new SaleInfo(eventId, "PUBLISHED", true,
                Instant.now().plusSeconds(10L * 86400),                      // el evento empieza en 10 días
                List.of(new SectorSale(sectorId, "Campo", "GENERAL", new BigDecimal("500.00"), "ARS"))));
        when(inventory.createHold(any(), any(), anyInt(), any()))
                .thenAnswer(i -> new HoldInfo(UUID.randomUUID(), Instant.now().plusSeconds(600), Map.of()));
        when(payments.charge(any())).thenAnswer(i -> {
            ChargeCommand c = i.getArgument(0);
            return new PaymentInfo(UUID.randomUUID(), c.orderId(), "APPROVED", "CARD", "4242");
        });
        when(tickets.issue(any(), any(), any(), any())).thenAnswer(i -> {
            List<TicketItem> items = i.getArgument(3);
            return items.stream().map(it -> new TicketInfo(ticketIdOf(it.orderItemId()), it.orderItemId(), eventId,
                    it.sectorId(), it.seatId(), "Campo", "qr", "VALID", Instant.now())).toList();
        });
    }

    // ------------------------------------------------------------------ helpers

    private OrderResponse confirmedOrder(Identity who, int quantity) {
        var created = createService.create(who, UUID.randomUUID().toString(),
                new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(sectorId, null, quantity)))).body();
        return payService.pay(who, created.orderId(), UUID.randomUUID().toString(),
                new PayOrderRequest(PayOrderRequest.PaymentMethod.CARD, "tok_approved", "4242"));
    }

    private UUID firstItem(OrderResponse order) {
        return order.items().get(0).itemId();
    }

    private RefundRequestResponse approvedRefund(OrderResponse order) {
        var req = requestService.request(customer, order.orderId(), firstItem(order), new CreateRefundRequest("No puedo asistir"));
        return decisionService.approve(admin, req.refundRequestId());
    }

    private RefundRequestResponse refundState(UUID id) {
        return refundQuery.listAll(admin, null, 0, 100).content().stream()
                .filter(r -> r.refundRequestId().equals(id)).findFirst().orElseThrow();
    }

    private int quotaUsed(Identity who) {
        return jdbc.queryForObject("select coalesce(sum(used), 0) from purchase_quota where customer_id = ? and event_id = ?",
                Integer.class, who.userId(), eventId);
    }

    private static void pause() {
        try {
            Thread.sleep(25);                                              // supera el backoff de los tests (1 ms, 2 ms, 4 ms ...)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ devoluciones

    @Test
    void devolucion_aprobada_invalida_libera_reintegra_y_deja_el_item_REFUNDED() {
        OrderResponse order = confirmedOrder(customer, 2);
        assertThat(quotaUsed(customer)).isEqualTo(2);

        RefundRequestResponse approved = approvedRefund(order);
        assertThat(approved.status()).isEqualTo(RefundRequestStatus.PROCESSING);
        recovery.runOnce();

        assertThat(refundState(approved.refundRequestId()).status()).isEqualTo(RefundRequestStatus.COMPLETED);
        UUID itemId = firstItem(order);
        verify(tickets).invalidate(ticketIdOf(itemId), "REFUNDED");
        verify(inventory).releaseSold(eq(eventId), eq(approved.refundRequestId()), any());          // releaseId = id de la solicitud
        verify(payments).refund(any(), eq(new BigDecimal("500.00")), anyString(), eq("refund-" + approved.refundRequestId()));
        var item = queryService.obtenerUno(customer, order.orderId()).items().stream().filter(i -> i.itemId().equals(itemId)).findFirst().orElseThrow();
        assertThat(item.status()).isEqualTo(OrderItemStatus.REFUNDED);
        assertThat(quotaUsed(customer)).isEqualTo(1);                       // la cuota del ítem devuelto se liberó
    }

    @Test
    void falla_transitoria_de_payment_se_reintenta_y_el_reintegro_se_hace_una_sola_vez() {
        OrderResponse order = confirmedOrder(customer, 1);
        doThrow(OrderException.servicioNoDisponible("payment caído")).doNothing()
                .when(payments).refund(any(), any(), anyString(), anyString());
        RefundRequestResponse approved = approvedRefund(order);

        recovery.runOnce();
        RefundRequestResponse afterFailure = refundState(approved.refundRequestId());
        assertThat(afterFailure.status()).isEqualTo(RefundRequestStatus.PROCESSING);
        assertThat(afterFailure.step()).isEqualTo(RefundStep.SEAT_RELEASED);      // los pasos previos ya estaban hechos
        assertThat(afterFailure.attempts()).isEqualTo(1);

        pause();
        recovery.runOnce();

        assertThat(refundState(approved.refundRequestId()).status()).isEqualTo(RefundRequestStatus.COMPLETED);
        verify(payments, times(2)).refund(any(), any(), anyString(), eq("refund-" + approved.refundRequestId()));   // misma clave
        verify(tickets, times(1)).invalidate(any(), anyString());                // no se repiten los pasos ya hechos
        verify(inventory, times(1)).releaseSold(any(), any(), any());
    }

    @Test
    void agotados_los_reintentos_la_solicitud_queda_FAILED_y_visible_para_el_admin() {
        OrderResponse order = confirmedOrder(customer, 1);
        doThrow(OrderException.servicioNoDisponible("payment caído")).when(payments).refund(any(), any(), anyString(), anyString());
        RefundRequestResponse approved = approvedRefund(order);

        for (int i = 0; i < 8; i++) {
            recovery.runOnce();
            pause();
        }

        RefundRequestResponse state = refundState(approved.refundRequestId());
        assertThat(state.status()).isEqualTo(RefundRequestStatus.FAILED);
        assertThat(state.attempts()).isEqualTo(5);
        assertThat(state.lastError()).contains("payment");
        assertThat(refundQuery.listAll(admin, RefundRequestStatus.FAILED, 0, 20).content())
                .extracting(RefundRequestResponse::refundRequestId).contains(approved.refundRequestId());
    }

    @Test
    void solicitud_duplicada_409_fuera_de_plazo_422_y_ajena_404() {
        OrderResponse order = confirmedOrder(customer, 2);
        UUID itemId = firstItem(order);
        requestService.request(customer, order.orderId(), itemId, null);

        assertThatThrownBy(() -> requestService.request(customer, order.orderId(), itemId, null))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));

        UUID other = order.items().get(1).itemId();
        jdbc.update("update purchase_order set event_starts_at = now() + interval '48 hours' where id = ?", order.orderId());
        assertThatThrownBy(() -> requestService.request(customer, order.orderId(), other, null))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("REFUND_WINDOW_CLOSED"));

        Identity stranger = new Identity(UUID.randomUUID(), Role.CUSTOMER);
        assertThatThrownBy(() -> requestService.request(stranger, order.orderId(), other, null))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void el_admin_rechaza_y_el_item_vuelve_a_ACTIVE_y_se_puede_pedir_de_nuevo() {
        OrderResponse order = confirmedOrder(customer, 1);
        UUID itemId = firstItem(order);
        var req = requestService.request(customer, order.orderId(), itemId, null);

        assertThat(decisionService.reject(admin, req.refundRequestId(), "Fuera de política").status())
                .isEqualTo(RefundRequestStatus.REJECTED);

        assertThat(queryService.obtenerUno(customer, order.orderId()).items().get(0).status()).isEqualTo(OrderItemStatus.ACTIVE);
        assertThat(requestService.request(customer, order.orderId(), itemId, null).status()).isEqualTo(RefundRequestStatus.PENDING);
        assertThatThrownBy(() -> decisionService.approve(admin, req.refundRequestId()))          // ya decidida
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION"));
    }

    @Test
    void un_customer_no_puede_decidir_y_las_pendientes_se_listan_por_antiguedad() {
        OrderResponse order = confirmedOrder(customer, 2);
        var first = requestService.request(customer, order.orderId(), order.items().get(0).itemId(), null);
        var second = requestService.request(customer, order.orderId(), order.items().get(1).itemId(), null);

        assertThatThrownBy(() -> decisionService.approve(customer, first.refundRequestId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("FORBIDDEN"));
        assertThatThrownBy(() -> refundQuery.listAll(customer, null, 0, 20)).isInstanceOf(ApiException.class);

        var pending = refundQuery.listAll(admin, RefundRequestStatus.PENDING, 0, 100).content().stream()
                .map(RefundRequestResponse::refundRequestId).toList();
        assertThat(pending).containsSubsequence(first.refundRequestId(), second.refundRequestId());
        assertThat(refundQuery.listarMios(customer, 0, 20).content()).hasSize(2);
    }

    // ------------------------------------------------------------------ cancelación de evento

    @Test
    void cancelar_el_evento_expira_pendientes_reintegra_confirmadas_y_es_idempotente() {
        Identity other = new Identity(UUID.randomUUID(), Role.CUSTOMER);
        OrderResponse a = confirmedOrder(customer, 2);
        OrderResponse b = confirmedOrder(other, 1);
        var pending = createService.create(new Identity(UUID.randomUUID(), Role.CUSTOMER), "p-1",
                new CreateOrderRequest(eventId, List.of(new CreateOrderRequest.Item(sectorId, null, 1)))).body();
        Identity pendingOwner = new Identity(pending.customerId(), Role.CUSTOMER);

        var first = cancelEventService.cancel(eventId, "Cancelado por el organizador");

        assertThat(first.created()).isTrue();
        assertThat(first.body().ordersExpired()).isEqualTo(1);
        assertThat(first.body().itemsToRefund()).isEqualTo(3);
        verify(inventory).closeEvent(eventId);
        assertThat(queryService.obtenerUno(pendingOwner, pending.orderId()).status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(queryService.obtenerUno(customer, a.orderId()).status()).isEqualTo(OrderStatus.CANCELLED_BY_EVENT);

        recovery.runOnce();

        long completed = jdbc.queryForObject("select count(*) from refund_request where event_id = ? and status = 'COMPLETED' and automatic",
                Long.class, eventId);
        assertThat(completed).isEqualTo(3);
        verify(tickets, times(3)).invalidate(any(), eq("CANCELLED"));
        cancelEventService.advance(eventId);          // segunda pasada (la del job): ya no quedan pendientes => invalidación en bloque
        verify(tickets).invalidateEvent(eventId, "CANCELLED");
        assertThat(queryService.obtenerUno(other, b.orderId()).items().get(0).status()).isEqualTo(OrderItemStatus.CANCELLED);

        // notificación repetida: 200, mismos números y sin reintegros duplicados
        var again = cancelEventService.cancel(eventId, "Cancelado por el organizador");
        assertThat(again.created()).isFalse();
        assertThat(again.body()).isEqualTo(first.body());
        cancelEventService.advance(eventId);
        long total = jdbc.queryForObject("select count(*) from refund_request where event_id = ?", Long.class, eventId);
        assertThat(total).isEqualTo(3);

        // un pago posterior sobre la orden expirada: 409 EVENT_NOT_PURCHASABLE
        assertThatThrownBy(() -> payService.pay(pendingOwner, pending.orderId(), "p-2",
                new PayOrderRequest(PayOrderRequest.PaymentMethod.CARD, "tok_approved", "4242")))
                .isInstanceOfSatisfying(OrderException.class, e -> assertThat(e.getCode()).isEqualTo("EVENT_NOT_PURCHASABLE"));
        verify(payments, times(2)).charge(any());                           // solo los 2 pagos originales
    }

    @Test
    void falla_parcial_de_payment_en_la_cancelacion_se_reintenta_sin_duplicar_los_ya_hechos() {
        OrderResponse a = confirmedOrder(customer, 1);
        OrderResponse b = confirmedOrder(new Identity(UUID.randomUUID(), Role.CUSTOMER), 1);
        // el primer reintegro falla una vez, después todo anda
        doThrow(OrderException.servicioNoDisponible("payment caído")).doNothing()
                .when(payments).refund(any(), any(), anyString(), anyString());

        cancelEventService.cancel(eventId, null);
        recovery.runOnce();
        pause();
        recovery.runOnce();

        long completed = jdbc.queryForObject("select count(*) from refund_request where event_id = ? and status = 'COMPLETED'",
                Long.class, eventId);
        assertThat(completed).isEqualTo(2);
        // 2 reintegros reales + 1 reintento del que falló; las claves distintas son 2 (una por solicitud)
        verify(payments, times(3)).refund(any(), any(), anyString(), anyString());
        long distinctKeys = org.mockito.Mockito.mockingDetails(payments).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("refund")).map(inv -> inv.<Object>getArgument(3)).distinct().count();
        assertThat(distinctKeys).isEqualTo(2);
    }

    @Test
    void si_inventory_esta_caido_la_cancelacion_se_registra_y_el_job_cierra_despues() {
        confirmedOrder(customer, 1);
        doThrow(OrderException.servicioNoDisponible("inventory caído")).doNothing().when(inventory).closeEvent(eventId);

        cancelEventService.cancel(eventId, null);
        assertThat(jdbc.queryForObject("select inventory_closed from event_cancellation where event_id = ?", Boolean.class, eventId))
                .isFalse();

        cancelEventService.avanzarTodasLasPendientes();                              // lo que haría el job
        assertThat(jdbc.queryForObject("select inventory_closed from event_cancellation where event_id = ?", Boolean.class, eventId))
                .isTrue();
        assertThat(jdbc.queryForObject("select settled from event_cancellation where event_id = ?", Boolean.class, eventId))
                .isTrue();
    }

    @Test
    void una_devolucion_manual_en_curso_se_absorbe_como_automatica_al_cancelar_el_evento() {
        OrderResponse order = confirmedOrder(customer, 1);
        var req = requestService.request(customer, order.orderId(), firstItem(order), null);      // PENDING, sin decidir

        cancelEventService.cancel(eventId, null);
        recovery.runOnce();

        RefundRequestResponse state = refundState(req.refundRequestId());
        assertThat(state.status()).isEqualTo(RefundRequestStatus.COMPLETED);
        assertThat(state.automatic()).isTrue();
        verify(tickets).invalidate(any(), eq("CANCELLED"));
        verify(tickets, never()).invalidate(any(), eq("REFUNDED"));
        long total = jdbc.queryForObject("select count(*) from refund_request where event_id = ?", Long.class, eventId);
        assertThat(total).isEqualTo(1);                                       // no se creó una segunda solicitud
    }

    @Test
    void cancelacion_con_100_ordenes_las_reintegra_todas() {
        for (int i = 0; i < 100; i++) {
            confirmedOrder(new Identity(UUID.randomUUID(), Role.CUSTOMER), 1);
        }

        var result = cancelEventService.cancel(eventId, "Cancelado");
        recovery.runOnce();

        assertThat(result.body().itemsToRefund()).isEqualTo(100);
        long completed = jdbc.queryForObject("select count(*) from refund_request where event_id = ? and status = 'COMPLETED'",
                Long.class, eventId);
        assertThat(completed).isEqualTo(100);
        long cancelledItems = jdbc.queryForObject("""
                select count(*) from order_item i join purchase_order o on o.id = i.order_id
                 where o.event_id = ? and i.status = 'CANCELLED'""", Long.class, eventId);
        assertThat(cancelledItems).isEqualTo(100);
        verify(tickets, times(100)).invalidate(any(), eq("CANCELLED"));
    }
}
