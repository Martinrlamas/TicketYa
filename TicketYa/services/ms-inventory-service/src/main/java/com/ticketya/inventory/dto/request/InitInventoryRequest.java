package com.ticketya.inventory.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ticketya.inventory.entity.SectorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/** Sectores del evento enviados por catalog al publicar. */
public record InitInventoryRequest(@NotEmpty @Valid List<SectorInit> sectors) {

    public record SectorInit(
            @NotNull UUID sectorId,
            @NotNull SectorType type,
            String name,
            @Valid List<RowInit> rows,
            @Positive Integer capacity) {

        @JsonIgnore
        @AssertTrue(message = "NUMBERED requiere rows; GENERAL requiere capacity")
        public boolean isConsistent() {
            if (type == SectorType.NUMBERED) {
                return rows != null && !rows.isEmpty() && capacity == null;
            }
            if (type == SectorType.GENERAL) {
                return capacity != null && (rows == null || rows.isEmpty());
            }
            return true; // type nulo lo reporta @NotNull
        }
    }

    public record RowInit(@NotBlank String rowLabel, @Positive int seatCount) {}
}
