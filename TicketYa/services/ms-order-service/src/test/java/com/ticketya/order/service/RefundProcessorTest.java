package com.ticketya.order.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketya.order.client.InventoryPort;
import com.ticketya.order.client.InventoryPort.StockItem;
import com.ticketya.order.client.PaymentPort;
import com.ticketya.order.client.TicketPort;
import com.ticketya.order.client.TicketPort.TicketInfo;
import com.ticketya.order.entity.RefundStep;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.notification.BuyerNotifier;
import com.ticketya.order.service.RefundTransactions.Completed;
import com.ticketya.order.service.RefundTransactions.Work;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Orden de los pasos, reanudación desde el último paso y manejo de fallas. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundProcessorTest {

    @Mock RefundTransactions txOps;
    @Mock TicketPort tickets;
    @Mock InventoryPort inventory;
    @Mock PaymentPort payments;
    @Mock BuyerNotifier notifier;

    RefundProcessor processor;
    final UUID requestId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();
    final UUID itemId = UUID.randomUUID();
    final UUID customerId = UUID.randomUUID();
    final UUID eventId = UUID.randomUUID();
    final UUID paymentId = UUID.randomUUID();
    final UUID sectorId = UUID.randomUUID();
    final UUID seatId = UUID.randomUUID();
    final UUID ticketId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new RefundProcessor(txOps, tickets, inventory, payments, notifier);
        when(tickets.issue(any(), any(), any(), any())).thenReturn(List.of(
                new TicketInfo(ticketId, itemId, eventId, sectorId, seatId, "A-5", "qr", "VALID", Instant.now())));
        when(txOps.complete(requestId)).thenReturn(
                new Completed(orderId, customerId, eventId, new BigDecimal("100.00"), "ARS", true));
    }

    private Work work(RefundStep step, boolean automatic, UUID seat) {
        return new Work(requestId, orderId, itemId, customerId, eventId, paymentId, sectorId, seat, "A-5",
                new BigDecimal("100.00"), "ARS", automatic, step);
    }

    @Test
    void ejecuta_los_pasos_en_orden_persistiendo_cada_uno() {
        when(txOps.load(requestId)).thenReturn(work(RefundStep.NONE, false, seatId));

        processor.process(requestId);

        var order = inOrder(tickets, txOps, inventory, payments);
        order.verify(tickets).invalidate(ticketId, "REFUNDED");
        order.verify(txOps).advance(requestId, RefundStep.TICKET_INVALIDATED);
        order.verify(inventory).releaseSold(eventId, requestId, List.of(StockItem.seats(sectorId, List.of(seatId))));
        order.verify(txOps).advance(requestId, RefundStep.SEAT_RELEASED);
        order.verify(payments).refund(paymentId, new BigDecimal("100.00"), "CUSTOMER_REFUND", "refund-" + requestId);
        order.verify(txOps).advance(requestId, RefundStep.PAYMENT_REFUNDED);
        order.verify(txOps).complete(requestId);
    }

    @Test
    void una_zona_general_libera_una_unidad_por_cantidad() {
        when(txOps.load(requestId)).thenReturn(work(RefundStep.NONE, false, null));
        processor.process(requestId);
        verify(inventory).releaseSold(eventId, requestId, List.of(StockItem.zone(sectorId, 1)));
    }

    @Test
    void reanuda_desde_el_ultimo_paso_sin_repetir_los_anteriores() {
        when(txOps.load(requestId)).thenReturn(work(RefundStep.SEAT_RELEASED, false, seatId));

        processor.process(requestId);

        verify(tickets, never()).invalidate(any(), anyString());
        verify(inventory, never()).releaseSold(any(), any(), any());
        verify(payments).refund(eq(paymentId), any(), anyString(), eq("refund-" + requestId));   // misma clave: un solo reintegro
        verify(txOps).complete(requestId);
    }

    @Test
    void falla_transitoria_de_payment_se_registra_para_reintentar_y_no_completa() {
        when(txOps.load(requestId)).thenReturn(work(RefundStep.SEAT_RELEASED, false, seatId));
        doThrow(OrderException.servicioNoDisponible("payment caído")).when(payments).refund(any(), any(), anyString(), anyString());

        processor.process(requestId);

        verify(txOps).recordFailure(eq(requestId), eq(true), anyString());
        verify(txOps, never()).complete(any());
    }

    @Test
    void entrada_ya_usada_es_una_falla_de_negocio_sin_reintentos() {
        when(txOps.load(requestId)).thenReturn(work(RefundStep.NONE, false, seatId));
        doThrow(OrderException.estadoInvalido("La entrada está USED")).when(tickets).invalidate(any(), anyString());

        processor.process(requestId);

        verify(txOps).recordFailure(eq(requestId), eq(false), anyString());
        verify(payments, never()).refund(any(), any(), anyString(), anyString());
    }

    @Test
    void el_reintegro_automatico_invalida_con_CANCELLED_y_avisa_al_comprador() {
        when(txOps.load(requestId)).thenReturn(work(RefundStep.NONE, true, seatId));

        processor.process(requestId);

        verify(tickets).invalidate(ticketId, "CANCELLED");
        verify(payments).refund(eq(paymentId), any(), eq("EVENT_CANCELLED"), eq("refund-" + requestId));
        verify(notifier).eventCancelled(customerId, orderId, eventId, new BigDecimal("100.00"), "ARS");
    }

    @Test
    void una_solicitud_manual_no_avisa_al_comprador() {
        when(txOps.load(requestId)).thenReturn(work(RefundStep.NONE, false, seatId));
        when(txOps.complete(requestId)).thenReturn(new Completed(orderId, customerId, eventId, BigDecimal.TEN, "ARS", false));
        processor.process(requestId);
        verify(notifier, never()).eventCancelled(any(), any(), any(), any(), anyString());
    }

    @Test
    void una_solicitud_que_ya_no_esta_PROCESSING_no_hace_nada() {
        when(txOps.load(requestId)).thenReturn(null);
        processor.process(requestId);
        verify(payments, never()).refund(any(), any(), anyString(), anyString());
        verify(txOps, never()).complete(any());
    }

    @Test
    void sin_entrada_emitida_para_el_item_es_falla_de_negocio() {
        when(txOps.load(requestId)).thenReturn(work(RefundStep.NONE, false, seatId));
        when(tickets.issue(any(), any(), any(), any())).thenReturn(List.of());
        processor.process(requestId);
        verify(txOps).recordFailure(eq(requestId), eq(false), anyString());
    }
}
