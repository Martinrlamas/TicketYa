package com.ticketya.order.service;

import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.entity.OrderItem;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.entity.RefundRequest;
import com.ticketya.order.entity.RefundRequestStatus;
import com.ticketya.order.entity.RefundStep;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.order.repository.RefundRequestRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Pasos transaccionales cortos de la saga de devolución. Ninguno hace llamadas remotas. */
@Component
public class RefundTransactions {

    /** Todo lo que el procesador necesita para ejecutar los pasos sin volver a tocar la base. */
    public record Work(UUID requestId, UUID orderId, UUID orderItemId, UUID customerId, UUID eventId, UUID paymentId,
                       UUID sectorId, UUID seatId, String seatLabel, BigDecimal amount, String currency,
                       boolean automatic, RefundStep step) {}

    public record Completed(UUID orderId, UUID customerId, UUID eventId, BigDecimal amount, String currency,
                            boolean automatic) {}

    private final RefundRequestRepository refundRepo;
    private final OrderRepository orderRepo;
    private final QuotaService quota;
    private final OrderProperties props;
    private final Clock clock;

    public RefundTransactions(RefundRequestRepository refundRepo, OrderRepository orderRepo, QuotaService quota,
                              OrderProperties props, Clock clock) {
        this.refundRepo = refundRepo;
        this.orderRepo = orderRepo;
        this.quota = quota;
        this.props = props;
        this.clock = clock;
    }

    /** Devuelve null si la solicitud ya no está PROCESSING (otro hilo la terminó): no hay nada que hacer. */
    @Transactional(readOnly = true)
    public Work load(UUID requestId) {
        RefundRequest r = refundRepo.findById(requestId).orElse(null);
        if (r == null || r.getStatus() != RefundRequestStatus.PROCESSING) {
            return null;
        }
        PurchaseOrder order = orderRepo.findById(r.getOrderId())
                .orElseThrow(() -> OrderException.noEncontrado("Orden inexistente: " + r.getOrderId()));
        OrderItem item = order.findItem(r.getOrderItemId());
        if (item == null) {
            throw OrderException.noEncontrado("Ítem inexistente: " + r.getOrderItemId());
        }
        return new Work(r.getId(), order.getId(), item.getId(), r.getCustomerId(), r.getEventId(), order.getPaymentId(),
                item.getSectorId(), item.getSeatId(), item.getSeatLabel(), item.getUnitPrice(), item.getCurrency(),
                r.isAutomatic(), r.getStep());
    }

    @Transactional
    public void advance(UUID requestId, RefundStep reached) {
        refundRepo.findByIdForUpdate(requestId).ifPresent(r -> r.advanceTo(reached, clock.instant()));
    }

    /** Ítem REFUNDED (o CANCELLED si fue por el evento), cuota liberada y solicitud COMPLETED. */
    @Transactional
    public Completed complete(UUID requestId) {
        UUID orderId = refundRepo.findOrderId(requestId)
                .orElseThrow(() -> OrderException.noEncontrado("Solicitud inexistente: " + requestId));
        PurchaseOrder order = orderRepo.findByIdForUpdate(orderId)                 // orden primero, después la solicitud
                .orElseThrow(() -> OrderException.noEncontrado("Orden inexistente: " + orderId));
        RefundRequest r = refundRepo.findByIdForUpdate(requestId).orElseThrow();
        if (r.getStatus() != RefundRequestStatus.PROCESSING) {
            return null;
        }
        OrderItem item = order.findItem(r.getOrderItemId());
        if (r.isAutomatic()) {
            item.cancelarPorEvento();
        } else {
            item.marcarReintegrado();
        }
        quota.release(r.getCustomerId(), r.getEventId(), 1);
        r.complete(clock.instant());
        return new Completed(order.getId(), r.getCustomerId(), r.getEventId(), item.getUnitPrice(), item.getCurrency(),
                r.isAutomatic());
    }

    /** Falla transitoria => reintento con backoff exponencial (hasta max-attempts); falla de negocio => FAILED ya. */
    @Transactional
    public void recordFailure(UUID requestId, boolean transientFailure, String message) {
        refundRepo.findByIdForUpdate(requestId).ifPresent(r -> {
            if (r.getStatus() != RefundRequestStatus.PROCESSING) {
                return;
            }
            Instant now = clock.instant();
            if (transientFailure) {
                r.registrarFalloTransitorio(message, props.refunds().maxAttempts(), now.plus(backoff(r.getAttempts())), now);
            } else {
                r.fallarDefinitivamente(message, now);
            }
        });
    }

    /** Reclama un lote de solicitudes vencidas con un lease: otra instancia no las toma mientras se procesan. */
    @Transactional
    public List<UUID> claimDue() {
        Instant now = clock.instant();
        List<RefundRequest> due = refundRepo.lockDue(now, props.refunds().batchSize());
        Instant until = now.plus(props.refunds().lease());
        due.forEach(r -> r.lease(until));
        return due.stream().map(RefundRequest::getId).toList();
    }

    Duration backoff(int failuresSoFar) {
        long factor = 1L << Math.min(Math.max(failuresSoFar, 0), 20);
        Duration d = props.refunds().baseBackoff().multipliedBy(factor);
        return d.compareTo(props.refunds().maxBackoff()) > 0 ? props.refunds().maxBackoff() : d;
    }
}
