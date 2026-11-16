package com.ticketya.ticket.dto.response;

import com.ticketya.ticket.entity.TicketStatus;
import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID ticketId,
        UUID orderItemId,
        UUID eventId,
        String eventName,
        UUID sectorId,
        UUID seatId,
        String seatLabel,
        String qrCode,
        TicketStatus status,
        Instant issuedAt) {}
