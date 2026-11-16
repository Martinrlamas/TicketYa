package com.ticketya.order.dto.response;

import com.ticketya.order.entity.OrderItemStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID itemId, UUID sectorId, UUID seatId, String seatLabel,
        BigDecimal unitPrice, String currency, OrderItemStatus status) {}
