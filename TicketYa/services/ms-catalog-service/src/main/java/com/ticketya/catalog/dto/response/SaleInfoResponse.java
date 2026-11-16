package com.ticketya.catalog.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.ticketya.catalog.entity.EventStatus;
import com.ticketya.catalog.entity.SectorType;

/** Respuesta de GET /internal/events/{id}/sale-info, consumida por order-service. */
public record SaleInfoResponse(UUID eventId, EventStatus status, boolean purchasable, OffsetDateTime startsAt,
        UUID organizerId, List<SaleSector> sectors) {

    public record SaleSector(UUID sectorId, String name, SectorType type, BigDecimal price, String currency) {
    }
}
