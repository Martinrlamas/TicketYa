package com.ticketya.catalog.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.ticketya.catalog.entity.EventStatus;
import com.ticketya.catalog.entity.EventType;

/** Nunca incluye disponibilidad de lugares: eso es de inventory-service. */
public record EventResponse(UUID id, String name, String description, EventType type, UUID organizerId,
        UUID venueId, String city, OffsetDateTime startsAt, EventStatus status,
        List<ParticipantResponse> participants, List<PriceResponse> prices, BigDecimal minPrice,
        OffsetDateTime createdAt) {
}
