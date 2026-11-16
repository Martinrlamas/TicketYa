package com.ticketya.catalog.dto.request;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.ticketya.catalog.entity.EventType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @NotNull EventType type,
        @NotNull UUID venueId,
        @NotNull OffsetDateTime startsAt,
        @Valid List<@NotNull ParticipantRequest> participants) {
}
