package com.ticketya.payment.dto.response;

import com.ticketya.payment.entity.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(UUID refundId, UUID paymentId, BigDecimal amount, RefundStatus status, Instant createdAt) {}
