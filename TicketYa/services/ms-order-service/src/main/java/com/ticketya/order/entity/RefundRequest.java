package com.ticketya.order.entity;

import com.ticketya.order.exception.OrderException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Solicitud de devolución de UNA entrada. Máquina de estados:
 * PENDING -> (approve) PROCESSING -> COMPLETED | FAILED ; PENDING -> (reject) REJECTED.
 * Las automáticas (cancelación de evento) nacen PROCESSING. APPROVED existe en el esquema por compatibilidad
 * con la spec pero la aprobación pasa directo a PROCESSING: no hay trabajo entre ambos estados.
 */
@Entity
@Table(name = "refund_request")
public class RefundRequest {

    @Id
    private UUID id;
    private UUID orderId;
    private UUID orderItemId;
    private UUID customerId;
    private UUID eventId;
    private String reason;
    private String decisionReason;

    @Enumerated(EnumType.STRING)
    private RefundRequestStatus status;

    @Enumerated(EnumType.STRING)
    private RefundStep step;

    private boolean automatic;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;
    private UUID decidedBy;
    private Instant decidedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected RefundRequest() {}

    public static RefundRequest pending(UUID orderId, UUID orderItemId, UUID customerId, UUID eventId, String reason,
                                        Instant now) {
        RefundRequest r = base(orderId, orderItemId, customerId, eventId, reason, now);
        r.status = RefundRequestStatus.PENDING;
        return r;
    }

    /** Reintegro por cancelación del evento: sin aprobación, listo para procesar. */
    public static RefundRequest automatic(UUID orderId, UUID orderItemId, UUID customerId, UUID eventId, String reason,
                                          Instant now) {
        RefundRequest r = base(orderId, orderItemId, customerId, eventId, reason, now);
        r.status = RefundRequestStatus.PROCESSING;
        r.automatic = true;
        r.nextAttemptAt = now;
        return r;
    }

    private static RefundRequest base(UUID orderId, UUID orderItemId, UUID customerId, UUID eventId, String reason,
                                      Instant now) {
        RefundRequest r = new RefundRequest();
        r.id = UUID.randomUUID();
        r.orderId = orderId;
        r.orderItemId = orderItemId;
        r.customerId = customerId;
        r.eventId = eventId;
        r.reason = reason;
        r.step = RefundStep.NONE;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    // ---------- transiciones ----------

    public void approve(UUID adminId, Instant now) {
        exigirEstado("aprobar", RefundRequestStatus.PENDING);
        this.status = RefundRequestStatus.PROCESSING;
        this.decidedBy = adminId;
        this.decidedAt = now;
        this.nextAttemptAt = now;
        this.updatedAt = now;
    }

    public void reject(UUID adminId, String decisionReason, Instant now) {
        exigirEstado("rechazar", RefundRequestStatus.PENDING);
        this.status = RefundRequestStatus.REJECTED;
        this.decidedBy = adminId;
        this.decidedAt = now;
        this.decisionReason = decisionReason;
        this.updatedAt = now;
    }

    /**
     * La cancelación del evento absorbe una solicitud manual todavía abierta: pasa a automática y se procesa
     * (conserva el paso ya alcanzado). Así la entrada se invalida siempre con el motivo CANCELLED.
     */
    public void absorberPorCancelacionEvento(Instant now) {
        if (!status.isActive()) {
            throw OrderException.estadoInvalido("La solicitud está " + status);
        }
        this.status = RefundRequestStatus.PROCESSING;
        this.automatic = true;
        this.nextAttemptAt = now;
        this.updatedAt = now;
    }

    /** Avanza el paso alcanzado; nunca retrocede (un procesador lento no pisa a uno más adelantado). */
    public void advanceTo(RefundStep reached, Instant now) {
        if (status == RefundRequestStatus.PROCESSING && step.isBefore(reached)) {
            this.step = reached;
            this.updatedAt = now;
        }
    }

    public void complete(Instant now) {
        exigirEstado("completar", RefundRequestStatus.PROCESSING);
        this.status = RefundRequestStatus.COMPLETED;
        this.step = RefundStep.PAYMENT_REFUNDED;
        this.nextAttemptAt = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    /** Falla transitoria: se reintenta {@code retryAt}. Al agotar los reintentos pasa a FAILED. */
    public void registrarFalloTransitorio(String error, int maxAttempts, Instant retryAt, Instant now) {
        exigirEstado("registrar una falla", RefundRequestStatus.PROCESSING);
        this.attempts++;
        this.lastError = truncate(error);
        this.updatedAt = now;
        if (attempts >= maxAttempts) {
            this.status = RefundRequestStatus.FAILED;
            this.nextAttemptAt = null;
        } else {
            this.nextAttemptAt = retryAt;
        }
    }

    /** Error de negocio que no se arregla reintentando (p. ej. la entrada ya fue usada). */
    public void fallarDefinitivamente(String error, Instant now) {
        exigirEstado("registrar una falla", RefundRequestStatus.PROCESSING);
        this.attempts++;
        this.status = RefundRequestStatus.FAILED;
        this.lastError = truncate(error);
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    /** El job reclama la solicitud por un tiempo: otra instancia no la toma mientras tanto. */
    public void lease(Instant until) {
        this.nextAttemptAt = until;
    }

    private void exigirEstado(String action, RefundRequestStatus expected) {
        if (status != expected) {
            throw OrderException.estadoInvalido("No se puede " + action + " una solicitud " + status);
        }
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() <= 300 ? s : s.substring(0, 300));
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getEventId() { return eventId; }
    public String getReason() { return reason; }
    public RefundRequestStatus getStatus() { return status; }
    public RefundStep getStep() { return step; }
    public boolean isAutomatic() { return automatic; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
