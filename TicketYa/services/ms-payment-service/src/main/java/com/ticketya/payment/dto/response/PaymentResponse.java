package com.ticketya.payment.dto.response;

import com.ticketya.payment.entity.PaymentMethod;
import com.ticketya.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Nunca incluye el token del medio de pago. */
public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String last4,
        PaymentStatus status,
        String gatewayRef,
        BigDecimal refundedAmount,
        Instant createdAt) {}
