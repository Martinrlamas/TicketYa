package com.ticketya.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketya.order.entity.RefundRequestStatus;
import com.ticketya.order.entity.RefundStep;
import java.time.Instant;
import java.util.UUID;

/** Los campos de seguimiento (step, attempts, lastError) permiten al ADMIN ver por qué quedó FAILED. */
public record RefundRequestResponse(
        UUID refundRequestId,
        UUID orderId,
        UUID orderItemId,
        UUID customerId,
        RefundRequestStatus status,
        String reason,
        Instant createdAt,
        UUID decidedBy,
        Instant decidedAt,
        boolean automatic,
        RefundStep step,
        int attempts,
        @JsonInclude(JsonInclude.Include.NON_NULL) String lastError) {}
