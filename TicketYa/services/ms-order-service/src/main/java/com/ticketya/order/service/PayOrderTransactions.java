package com.ticketya.order.service;

import com.ticketya.order.entity.OrderStatus;
import com.ticketya.order.entity.PayAttempt;
import com.ticketya.order.entity.PayAttemptResult;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.exception.OrderException;
import com.ticketya.order.repository.EventCancellationRepository;
import com.ticketya.order.repository.OrderRepository;
import com.ticketya.order.repository.PayAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pasos transaccionales CORTOS de la saga de pago. Ninguno hace llamadas remotas: el orquestador
 * ({@link PayOrderService}) alterna estos pasos con las llamadas a otros servicios, de modo que nunca
 * hay una conexión de base abierta durante una llamada HTTP.
 * <p>
 * Todos bloquean primero la fila de la orden (FOR UPDATE) y son idempotentes: si una reanudación o una
 * solicitud concurrente repite un paso que ya se aplicó, no hace nada.
 */
@Component
public class PayOrderTransactions {

    public enum StartKind { PROCEED, REPLAY_CONFIRMED, REPLAY_REJECTED, REPLAY_COMPENSATED, EXPIRED_NOW, EVENT_CANCELLED }

    public record Start(StartKind kind, String reason, UUID holdId) {
        static Start of(StartKind kind) {
            return new Start(kind, null, null);
        }
    }

    private final OrderRepository orderRepo;
    private final PayAttemptRepository attemptRepo;
    private final QuotaService quota;
    private final EventCancellationRepository cancellations;
    private final Clock clock;

    public PayOrderTransactions(OrderRepository orderRepo, PayAttemptRepository attemptRepo, QuotaService quota,
                                EventCancellationRepository cancellations, Clock clock) {
        this.orderRepo = orderRepo;
        this.attemptRepo = attemptRepo;
        this.quota = quota;
        this.cancellations = cancellations;
        this.clock = clock;
    }

    /**
     * Decide cómo sigue un pedido de pago. Con la misma Idempotency-Key devuelve el resultado original;
     * con otra clave solo avanza si no hay un pago en curso. Registra el intento PENDING (la "reserva de pago").
     */
    @Transactional
    public Start begin(UUID orderId, UUID customerId, String clientKey) {
        PurchaseOrder order = orderRepo.findByIdForUpdate(orderId)
                .filter(o -> o.esDuenio(customerId))                         // ajena => 404, no se revela que existe
                .orElseThrow(() -> OrderException.noEncontrado("Orden inexistente: " + orderId));
        String key = orderId + ":" + clientKey;
        Instant now = clock.instant();

        Optional<PayAttempt> same = attemptRepo.findByIdempotencyKey(key);
        if (same.isPresent()) {
            PayAttempt attempt = same.get();
            switch (attempt.getResult()) {
                case COMPLETED: return Start.of(StartKind.REPLAY_CONFIRMED);
                case REJECTED: return Start.of(StartKind.REPLAY_REJECTED);
                case COMPENSATED: return new Start(StartKind.REPLAY_COMPENSATED, attempt.getReason(), null);
                case PENDING:
                case APPROVED:
                case COMPENSATING: return Start.of(StartKind.PROCEED);          // reanudar: los pasos siguientes son idempotentes
                case ABANDONED:
                    if (order.isPending() && !order.reservaExpirada(now)) {
                        attempt.reopen(now);
                        return Start.of(StartKind.PROCEED);
                    }
                    break;                                                      // sigue a las validaciones de estado
                default: break;
            }
        }

        boolean eventCancelled = cancellations.existsById(order.getEventId());
        if (order.getStatus() == OrderStatus.EXPIRED) {
            throw eventCancelled
                    ? OrderException.eventoNoComprable("El evento fue cancelado")
                    : OrderException.reservaVencida("La reserva de la orden " + orderId + " venció");
        }
        if (!order.isPending()) {
            throw OrderException.estadoInvalido("La orden está " + order.getStatus());
        }
        if (!attemptRepo.findByOrderIdAndResultIn(orderId, PayAttemptResult.IN_FLIGHT).isEmpty()) {
            throw OrderException.estadoInvalido("Hay un pago en curso para esta orden");   // "solo un pago se procesa"
        }
        if (eventCancelled) {                                       // evento cancelado: no se cobra, la orden expira
            order.expire();
            quota.release(order);
            return new Start(StartKind.EVENT_CANCELLED, null, order.getHoldId());
        }
        if (order.reservaExpirada(now)) {
            order.expire();
            quota.release(order);
            return new Start(StartKind.EXPIRED_NOW, null, order.getHoldId());
        }
        attemptRepo.save(PayAttempt.start(orderId, key, now));
        return Start.of(StartKind.PROCEED);
    }

    /** Pago RECHAZADO: orden PAYMENT_REJECTED y cuota liberada. Devuelve el hold para liberarlo fuera de la transacción. */
    @Transactional
    public UUID recordRejected(UUID orderId) {
        PurchaseOrder order = lock(orderId);
        inFlight(orderId).ifPresent(a -> {
            if (a.getResult() == PayAttemptResult.PENDING) {
                a.reject(clock.instant());
            }
        });
        if (order.isPending()) {
            order.rechazarPago();
            quota.release(order);
        }
        return order.getHoldId();
    }

    /** Pago APROBADO: se guarda el paymentId; la orden sigue PENDING_PAYMENT hasta vender y emitir. */
    @Transactional
    public void recordApproved(UUID orderId, UUID paymentId, String method, String last4) {
        PurchaseOrder order = lock(orderId);
        inFlight(orderId).ifPresent(a -> {
            if (a.getResult() == PayAttemptResult.PENDING) {
                a.approve(clock.instant());
            }
        });
        if (order.isPending()) {
            order.registrarPagoAprobado(paymentId, method, last4);
        }
    }

    /** Venta confirmada y entradas emitidas: orden CONFIRMED. */
    @Transactional
    public void completeConfirmed(UUID orderId) {
        PurchaseOrder order = lock(orderId);
        if (!order.isPending()) {
            return;                                                  // otro hilo ya la cerró
        }
        inFlight(orderId).ifPresent(a -> {
            if (a.getResult() == PayAttemptResult.APPROVED) {
                a.complete(clock.instant());
            }
        });
        order.confirm();
    }

    /**
     * Deja asentado que hay que reintegrar. Desde acá la reanudación solo puede CONTINUAR la compensación,
     * nunca volver a vender (el reintegro ya pudo haberse hecho).
     */
    @Transactional
    public void iniciarCompensacion(UUID orderId, String reason) {
        lock(orderId);
        inFlight(orderId).ifPresent(a -> a.iniciarCompensacion(reason, clock.instant()));
    }

    /** Reintegro y liberación hechos: orden FAILED_REFUNDED y cuota liberada. */
    @Transactional
    public void finalizarCompensacion(UUID orderId) {
        PurchaseOrder order = lock(orderId);
        attemptRepo.findByOrderIdAndResultIn(orderId, EnumSet.of(PayAttemptResult.COMPENSATING))
                .forEach(a -> a.finalizarCompensacion(clock.instant()));
        if (order.isPending()) {
            order.fallarReintegrado();
            quota.release(order);
        }
    }

    /** Sin rastro del cobro en payment-service tras el plazo: se cierra el intento (el cliente puede reintentar). */
    @Transactional
    public void abandon(UUID orderId) {
        lock(orderId);
        inFlight(orderId).ifPresent(a -> {
            if (a.getResult() == PayAttemptResult.PENDING) {
                a.abandon(clock.instant());
            }
        });
    }

    private PurchaseOrder lock(UUID orderId) {
        return orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderException.noEncontrado("Orden inexistente: " + orderId));
    }

    private Optional<PayAttempt> inFlight(UUID orderId) {
        List<PayAttempt> attempts = attemptRepo.findByOrderIdAndResultIn(orderId, PayAttemptResult.IN_FLIGHT);
        return attempts.stream().findFirst();
    }
}
