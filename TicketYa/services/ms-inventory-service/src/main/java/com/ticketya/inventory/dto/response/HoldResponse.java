package com.ticketya.inventory.dto.response;

import com.ticketya.inventory.entity.HoldStatus;
import java.time.Instant;
import java.util.UUID;

import java.util.List;

/** {@code seats} lista los asientos numerados retenidos (vacío si el hold es solo de zonas generales). */
public record HoldResponse(UUID holdId, UUID orderId, HoldStatus status, Instant expiresAt, List<HeldSeat> seats) {

    public record HeldSeat(UUID seatId, UUID sectorId, String label) {}
}
