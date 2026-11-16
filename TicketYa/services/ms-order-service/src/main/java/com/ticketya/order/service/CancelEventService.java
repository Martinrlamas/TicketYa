package com.ticketya.order.service;

import com.ticketya.order.client.InventoryPort;
import com.ticketya.order.client.TicketPort;
import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.dto.response.CancelEventResponse;
import com.ticketya.order.entity.EventCancellation;
import com.ticketya.order.entity.OrderItem;
import com.ticketya.order.entity.OrderItemStatus;
import com.ticketya.order.entity.OrderStatus;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.entity.RefundRequest;
import com.ticketya.order.entity.RefundRequestStatus;
import com.ticketya.order.exception.OrderException;
import com.ticketya.platform.error.ErrorCode;
import com.ticketya.order.repository.EventCancellationRepository;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.order.repository.RefundRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cancelación de un evento (la dispara catalog). Es idempotente por eventId y REANUDABLE: {@link #advance}
 * se puede repetir las veces que haga falta (el job la repite hasta dejarla "settled").
 * <ol>
 *   <li>registra la cancelación (INSERT ... ON CONFLICT DO NOTHING) y cierra el inventario;</li>
 *   <li>expira las órdenes PENDING_PAYMENT sin pago en curso (liberando cuota);</li>
 *   <li>pasa las órdenes CONFIRMED a CANCELLED_BY_EVENT, sus ítems a CANCELLED y crea un reintegro automático
 *       por ítem (las solicitudes manuales todavía abiertas se absorben);</li>
 *   <li>cuando ya no queda ninguna orden pendiente, pide la invalidación masiva de entradas.</li>
 * </ol>
 * Los reintegros los ejecuta el {@link RefundProcessor} (job de recuperación). Las órdenes con un pago en curso se
 * resuelven por reconciliación; si terminan CONFIRMED, la siguiente pasada las convierte en reintegro.
 */
@Service
public class CancelEventService {

    private static final Logger log = LoggerFactory.getLogger(CancelEventService.class);
    private static final String DEFAULT_REASON = "Evento cancelado";

    public record Result(CancelEventResponse body, boolean created) {}

    private final EventCancellationRepository cancellationRepo;
    private final OrderRepository orderRepo;
    private final RefundRequestRepository refundRepo;
    private final QuotaService quota;
    private final InventoryPort inventory;
    private final TicketPort tickets;
    private final OrderProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CancelEventService(EventCancellationRepository cancellationRepo, OrderRepository orderRepo,
                              RefundRequestRepository refundRepo, QuotaService quota, InventoryPort inventory,
                              TicketPort tickets, OrderProperties props, TransactionTemplate tx, Clock clock) {
        this.cancellationRepo = cancellationRepo;
        this.orderRepo = orderRepo;
        this.refundRepo = refundRepo;
        this.quota = quota;
        this.inventory = inventory;
        this.tickets = tickets;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    /** 202 la primera vez; 200 (sin reintegros duplicados) si ya estaba registrada. */
    public Result cancel(UUID eventId, String reason) {
        String effectiveReason = reason == null || reason.isBlank() ? DEFAULT_REASON : reason.trim();
        Integer inserted = tx.execute(s -> cancellationRepo.insertIfAbsent(eventId, effectiveReason, clock.instant()));
        boolean created = inserted != null && inserted == 1;
        if (created) {
            advance(eventId);
        }
        EventCancellation c = cancellationRepo.findById(eventId).orElseThrow();
        return new Result(new CancelEventResponse(eventId, c.getOrdersExpired(), c.getItemsToRefund()), created);
    }

    /** Un ciclo de procesamiento. Idempotente: no genera reintegros duplicados al repetirse. */
    public void advance(UUID eventId) {
        EventCancellation c = cancellationRepo.findById(eventId).orElse(null);
        if (c == null || c.isSettled()) {
            return;
        }
        if (!c.isInventoryClosed()) {
            intentarCerrarInventario(eventId);
        }
        // Se mira ANTES de barrer: si no había nada pendiente, ninguna orden puede pasar a CONFIRMED después del barrido
        boolean pendingBefore = orderRepo.existsByEventIdAndStatus(eventId, OrderStatus.PENDING_PAYMENT);
        sweep(eventId, c.getReason());
        if (pendingBefore) {
            return;                                           // hay pagos en curso: la próxima pasada los resuelve
        }
        intentarInvalidarEntradas(eventId);
        tx.executeWithoutResult(s -> cancellationRepo.findByIdForUpdate(eventId).ifPresent(x -> {
            if (x.isInventoryClosed() && x.isTicketsInvalidated()) {
                x.markSettled(clock.instant());
            }
        }));
    }

    /** Pasada de las cancelaciones que todavía tienen trabajo pendiente (la usa el job). */
    public void avanzarTodasLasPendientes() {
        for (EventCancellation c : cancellationRepo.findBySettledFalse()) {
            try {
                advance(c.getEventId());
            } catch (RuntimeException e) {
                log.error("falló el procesamiento de la cancelación del evento {}", c.getEventId(), e);
            }
        }
    }

    // ------------------------------------------------------------------ barrido por lotes

    private void sweep(UUID eventId, String reason) {
        int batch = props.cancellation().batchSize();
        int expired = 0;
        int items = 0;
        int n;
        do {
            n = tx.execute(s -> expirarPendientesLote(eventId, batch));
            expired += n;
        } while (n > 0);
        int[] converted;
        do {
            converted = tx.execute(s -> convertirConfirmadasLote(eventId, batch, reason));
            items += converted[1];
        } while (converted[0] > 0);
        final int e = expired;
        final int i = items;
        if (e > 0 || i > 0) {
            tx.executeWithoutResult(s -> cancellationRepo.findByIdForUpdate(eventId)
                    .ifPresent(c -> c.addProgress(e, i, clock.instant())));
            log.info("evento {} cancelado: {} órdenes expiradas, {} ítems a reintegrar", eventId, e, i);
        }
    }

    private int expirarPendientesLote(UUID eventId, int batch) {
        List<PurchaseOrder> orders = orderRepo.lockPendingByEvent(eventId, batch);
        for (PurchaseOrder o : orders) {
            o.expire();
            quota.release(o);
        }
        return orders.size();
    }

    /** @return {órdenes procesadas, ítems a reintegrar}. Cada orden pasa a CANCELLED_BY_EVENT, así no se repite. */
    private int[] convertirConfirmadasLote(UUID eventId, int batch, String reason) {
        List<PurchaseOrder> orders = orderRepo.lockConfirmedByEvent(eventId, batch);
        Instant now = clock.instant();
        int items = 0;
        for (PurchaseOrder o : orders) {
            o.cancelarPorEvento();
            List<UUID> itemIds = o.getItems().stream().map(OrderItem::getId).toList();
            Map<UUID, RefundRequest> open = refundRepo
                    .findByOrderItemIdInAndStatusIn(itemIds, RefundRequestStatus.ACTIVE).stream()
                    .collect(Collectors.toMap(RefundRequest::getOrderItemId, Function.identity()));
            for (OrderItem item : o.getItems()) {
                RefundRequest existing = open.get(item.getId());
                if (existing != null) {
                    existing.absorberPorCancelacionEvento(now);          // la devolución manual en curso pasa a automática
                    item.cancelarPorEvento();
                    items++;
                } else if (item.getStatus() == OrderItemStatus.ACTIVE) {
                    refundRepo.save(RefundRequest.automatic(o.getId(), item.getId(), o.getCustomerId(), o.getEventId(),
                            reason, now));
                    item.cancelarPorEvento();
                    items++;
                }
                // REFUNDED / CANCELLED: ya reintegrado. REFUND_REQUESTED sin solicitud activa (FAILED): lo resuelve un ADMIN.
            }
        }
        return new int[] {orders.size(), items};
    }

    // ------------------------------------------------------------------ llamadas a otros servicios

    private void intentarCerrarInventario(UUID eventId) {
        try {
            inventory.closeEvent(eventId);
        } catch (OrderException e) {
            if (!e.hasCode(ErrorCode.NOT_FOUND)) {              // NOT_FOUND: el evento no tenía inventario, no hay nada que cerrar
                log.warn("no se pudo cerrar el inventario del evento {} (se reintenta): {}", eventId, e.getMessage());
                return;
            }
        }
        tx.executeWithoutResult(s -> cancellationRepo.findByIdForUpdate(eventId)
                .ifPresent(c -> c.markInventoryClosed(clock.instant())));
    }

    private void intentarInvalidarEntradas(UUID eventId) {
        try {
            int n = tickets.invalidateEvent(eventId, "CANCELLED");
            log.info("evento {}: {} entradas invalidadas en bloque", eventId, n);
        } catch (OrderException e) {
            log.warn("no se pudo invalidar en bloque las entradas del evento {} (se reintenta): {}", eventId, e.getMessage());
            return;
        }
        tx.executeWithoutResult(s -> cancellationRepo.findByIdForUpdate(eventId)
                .ifPresent(c -> c.markTicketsInvalidated(clock.instant())));
    }
}
