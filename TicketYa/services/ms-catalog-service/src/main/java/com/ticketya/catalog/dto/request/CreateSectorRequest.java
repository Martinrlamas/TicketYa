package com.ticketya.catalog.dto.request;

import java.util.List;

import com.ticketya.catalog.entity.SectorType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** NUMBERED exige rows y no admite capacity; GENERAL exige capacity y no admite rows. */
public record CreateSectorRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull SectorType type,
        Integer capacity,
        @Valid List<@NotNull RowRequest> rows) {
}
