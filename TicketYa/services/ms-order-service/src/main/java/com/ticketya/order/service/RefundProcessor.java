package com.ticketya.order.service;

import com.ticketya.order.client.InventoryPort;
import com.ticketya.order.client.InventoryPort.StockItem;
import com.ticketya.order.client.PaymentPort;
import com.ticketya.order.client.TicketPort;
import com.ticketya.order.client.TicketPort.TicketInfo;
import com.ticketya.order.client.TicketPort.TicketItem;
import com.ticketya.order.entity.RefundStep;
import com.ticketya.order.exception.OrderException;
import com.ticketya.platform.error.ErrorCode;
import com.ticketya.order.notification.BuyerNotifier;
import com.ticketya.order.service.RefundTransactions.Completed;
import com.ticketya.order.service.RefundTransactions.Work;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Saga de devolución de una entrada: invalidar entrada -> devolver el lugar -> reintegrar -> cerrar.
 * Orquestador sin transacción propia: alterna pasos de base cortos con llamadas remotas, todas idempotentes
 * (invalidación con el mismo motivo, releaseId = id de la solicitud, clave refund-{id} en payment).
 * El paso alcanzado se persiste, así una reanudación sigue desde ahí y el reintegro nunca se duplica.
 * Es roll-forward: la decisión de reintegrar ya fue tomada, nunca se deshace un paso.
 */
@Service
public class RefundProcessor {

    private static final Logger log = LoggerFactory.getLogger(RefundProcessor.class);

    private final RefundTransactions txOps;
    private final TicketPort tickets;
    private final InventoryPort inventory;
    private final PaymentPort payments;
    private final BuyerNotifier notifier;

    public RefundProcessor(RefundTransactions txOps, TicketPort tickets, InventoryPort inventory, PaymentPort payments,
                           BuyerNotifier notifier) {
        this.txOps = txOps;
        this.tickets = tickets;
        this.inventory = inventory;
        this.payments = payments;
        this.notifier = notifier;
    }

    /** Ejecuta (o reanuda) la solicitud. No propaga errores: los registra en la solicitud para el reintento. */
    public void process(UUID requestId) {
        Work work;
        try {
            work = txOps.load(requestId);
        } catch (OrderException e) {
            txOps.recordFailure(requestId, false, e.getMessage());
            return;
        }
        if (work == null) {
            return;
        }
        try {
            run(work);
            Completed done = txOps.complete(requestId);
            if (done != null && done.automatic()) {
                notifier.eventCancelled(done.customerId(), done.orderId(), done.eventId(), done.amount(), done.currency());
            }
            log.info("refund completed requestId={} orderItemId={} automatic={}", requestId, work.orderItemId(), work.automatic());
        } catch (OrderException e) {
            boolean transientFailure = e.hasCode(ErrorCode.UPSTREAM_UNAVAILABLE);
            log.warn("refund requestId={} falló en el paso posterior a {} ({}transitoria): {} {}", requestId, work.step(),
                    transientFailure ? "" : "NO ", e.getCode(), e.getMessage());
            txOps.recordFailure(requestId, transientFailure, e.getCode() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("refund requestId={} falló inesperadamente", requestId, e);
            txOps.recordFailure(requestId, true, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void run(Work w) {
        UUID id = w.requestId();
        if (w.step().isBefore(RefundStep.TICKET_INVALIDATED)) {
            tickets.invalidate(ticketIdOf(w), w.automatic() ? "CANCELLED" : "REFUNDED");
            txOps.advance(id, RefundStep.TICKET_INVALIDATED);
        }
        if (w.step().isBefore(RefundStep.SEAT_RELEASED)) {
            inventory.releaseSold(w.eventId(), id, List.of(stockOf(w)));
            txOps.advance(id, RefundStep.SEAT_RELEASED);
        }
        if (w.step().isBefore(RefundStep.PAYMENT_REFUNDED)) {
            payments.refund(w.paymentId(), w.amount(), w.automatic() ? "EVENT_CANCELLED" : "CUSTOMER_REFUND",
                    "refund-" + id);
            txOps.advance(id, RefundStep.PAYMENT_REFUNDED);
        }
    }

    /**
     * ticket-service no expone una consulta por ítem: se usa la emisión (idempotente por orderItemId), que
     * devuelve la entrada ya emitida. Conviene un {@code GET /internal/tickets?orderItemId=} (ver README).
     */
    private UUID ticketIdOf(Work w) {
        List<TicketInfo> found = tickets.issue(w.orderId(), w.eventId(), w.customerId(),
                List.of(new TicketItem(w.orderItemId(), w.sectorId(), w.seatId(), w.seatLabel())));
        return found.stream().filter(t -> w.orderItemId().equals(t.orderItemId())).findFirst()
                .orElseThrow(() -> OrderException.noEncontrado("No hay entrada emitida para el ítem " + w.orderItemId()))
                .ticketId();
    }

    private static StockItem stockOf(Work w) {
        return w.seatId() != null
                ? StockItem.seats(w.sectorId(), List.of(w.seatId()))
                : StockItem.zone(w.sectorId(), 1);
    }
}
