package com.ticketya.order.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID eventId,
        @NotEmpty @Valid List<Item> items) {

    /** Sector numerado: {@code seatIds}. Zona general: {@code quantity}. Exactamente uno de los dos. */
    public record Item(
            @NotNull UUID sectorId,
            List<UUID> seatIds,
            @Positive Integer quantity) {

        @JsonIgnore
        @AssertTrue(message = "enviar seatIds o quantity, no ambos ni ninguno")
        public boolean isSeatsXorQuantity() {
            boolean hasSeats = seatIds != null && !seatIds.isEmpty();
            return hasSeats ^ (quantity != null);
        }

        @JsonIgnore
        public boolean isNumbered() {
            return seatIds != null && !seatIds.isEmpty();
        }
    }
}
