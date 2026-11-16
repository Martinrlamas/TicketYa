package com.ticketya.order.entity;

import com.ticketya.order.exception.OrderException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Intento de pago de una orden: registra en qué paso de la saga se está (ver {@link PayAttemptResult}). */
@Entity
@Table(name = "pay_attempt")
public class PayAttempt {

    public static final String REASON_HOLD_EXPIRED = "HOLD_EXPIRED";
    public static final String REASON_TICKETS_FAILED = "TICKETS_FAILED";

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private UUID orderId;
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    private PayAttemptResult result;

    private String reason;
    private Instant createdAt;
    private Instant updatedAt;

    protected PayAttempt() {}

    public static PayAttempt start(UUID orderId, String idempotencyKey, Instant now) {
        PayAttempt a = new PayAttempt();
        a.orderId = orderId;
        a.idempotencyKey = idempotencyKey;
        a.result = PayAttemptResult.PENDING;
        a.createdAt = now;
        a.updatedAt = now;
        return a;
    }

    public void approve(Instant now) {
        move(now, PayAttemptResult.APPROVED, PayAttemptResult.PENDING, PayAttemptResult.APPROVED);
    }

    public void reject(Instant now) {
        move(now, PayAttemptResult.REJECTED, PayAttemptResult.PENDING);
    }

    public void complete(Instant now) {
        move(now, PayAttemptResult.COMPLETED, PayAttemptResult.APPROVED);
    }

    public void iniciarCompensacion(String reason, Instant now) {
        if (result == PayAttemptResult.COMPENSATING) {
            return;                                   // idempotente: se conserva el motivo original
        }
        move(now, PayAttemptResult.COMPENSATING, PayAttemptResult.APPROVED);
        this.reason = reason;
    }

    public void finalizarCompensacion(Instant now) {
        move(now, PayAttemptResult.COMPENSATED, PayAttemptResult.COMPENSATING);
    }

    public void abandon(Instant now) {
        move(now, PayAttemptResult.ABANDONED, PayAttemptResult.PENDING);
    }

    /** Un cliente reintenta con la misma clave un intento abandonado: vuelve a PENDING. */
    public void reopen(Instant now) {
        move(now, PayAttemptResult.PENDING, PayAttemptResult.ABANDONED);
    }

    private void move(Instant now, PayAttemptResult target, PayAttemptResult... allowedFrom) {
        for (PayAttemptResult from : allowedFrom) {
            if (result == from) {
                this.result = target;
                this.updatedAt = now;
                return;
            }
        }
        throw OrderException.estadoInvalido("Intento de pago " + result + " no puede pasar a " + target);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public PayAttemptResult getResult() { return result; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isInFlight() { return result.isInFlight(); }
}
