package com.ticketya.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketya.inventory.entity.SeatStatus;
import com.ticketya.inventory.entity.SectorType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(UUID eventId, Instant generatedAt, List<SectorAvailability> sectors) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SectorAvailability(
            UUID sectorId,
            String name,
            SectorType type,
            int capacity,
            int available,
            List<SeatAvailability> seats) {}   // solo sectores NUMBERED

    public record SeatAvailability(UUID seatId, String rowLabel, int number, SeatStatus status) {}
}
