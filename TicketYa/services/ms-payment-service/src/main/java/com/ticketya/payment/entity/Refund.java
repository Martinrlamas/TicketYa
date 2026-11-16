package com.ticketya.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "refund")
public class Refund {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private UUID paymentId;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    private String reason;
    private String idempotencyKey;
    private Instant createdAt;

    protected Refund() {}

    public static Refund create(UUID paymentId, BigDecimal amount, RefundStatus status, String reason,
                                String idempotencyKey, Instant createdAt) {
        Refund r = new Refund();
        r.paymentId = paymentId;
        r.amount = amount;
        r.status = status;
        r.reason = reason;
        r.idempotencyKey = idempotencyKey;
        r.createdAt = createdAt;
        return r;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public RefundStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
