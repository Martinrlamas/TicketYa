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

/**
 * Pago. {@code refundedAmount} NO tiene setter: solo lo modifica el UPDATE condicional del repository
 * (garantiza que la suma de reintegros nunca supere {@code amount}, aun con reintegros concurrentes).
 * El token del medio de pago se guarda pero nunca sale en toString, logs ni responses.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private UUID orderId;
    private UUID customerId;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    private String methodToken;
    private String last4;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String gatewayRef;
    private String idempotencyKey;
    private String requestHash;

    @Column(precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    private Instant createdAt;

    protected Payment() {}

    public static Payment create(UUID orderId, UUID customerId, BigDecimal amount, String currency,
                                 PaymentMethod method, String methodToken, String last4,
                                 PaymentStatus status, String gatewayRef, String idempotencyKey,
                                 String requestHash, Instant createdAt) {
        Payment p = new Payment();
        p.orderId = orderId;
        p.customerId = customerId;
        p.amount = amount;
        p.currency = currency;
        p.method = method;
        p.methodToken = methodToken;
        p.last4 = last4;
        p.status = status;
        p.gatewayRef = gatewayRef;
        p.idempotencyKey = idempotencyKey;
        p.requestHash = requestHash;
        p.createdAt = createdAt;
        return p;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentMethod getMethod() { return method; }
    public String getLast4() { return last4; }
    public PaymentStatus getStatus() { return status; }
    public String getGatewayRef() { return gatewayRef; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isApproved() { return status == PaymentStatus.APPROVED; }

    @Override
    public String toString() {
        return "Payment[id=" + id + ", orderId=" + orderId + ", status=" + status + "]";   // sin token ni last4
    }
}
