package com.ticketya.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketya.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID customerId,
        UUID eventId,
        Instant eventStartsAt,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal total,
        String currency,
        Instant holdExpiresAt,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID paymentId,
        @JsonInclude(JsonInclude.Include.NON_NULL) PaymentSummary payment,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<TicketDto> tickets) {

    public OrderResponse withTickets(List<TicketDto> newTickets) {
        return new OrderResponse(orderId, customerId, eventId, eventStartsAt, status, items, total, currency,
                holdExpiresAt, createdAt, paymentId, payment, newTickets);
    }
}
