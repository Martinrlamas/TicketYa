package com.ticketya.catalog.dto.request;

import com.ticketya.catalog.entity.ParticipantRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ParticipantRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull ParticipantRole role) {
}
