package com.ticketya.inventory.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/** Ítem de hold o de liberación: asientos concretos (sector numerado) o cantidad (zona general). */
public record StockItemRequest(
        @NotNull UUID sectorId,
        List<UUID> seatIds,
        @Positive Integer quantity) {

    @JsonIgnore
    @AssertTrue(message = "enviar seatIds o quantity, no ambos ni ninguno")
    public boolean isSeatsXorQuantity() {
        boolean hasSeats = seatIds != null && !seatIds.isEmpty();
        boolean hasQty = quantity != null;
        return hasSeats ^ hasQty;
    }

    @JsonIgnore
    public boolean isNumbered() {
        return seatIds != null && !seatIds.isEmpty();
    }
}
