package com.ticketya.order.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Entrada tal como la ve el cliente de order-service (DTO propio, no el de ticket-service). */
public record TicketDto(
        UUID ticketId, UUID orderItemId, UUID eventId, UUID sectorId, UUID seatId,
        String seatLabel, String qrCode, String status, Instant issuedAt) {}
