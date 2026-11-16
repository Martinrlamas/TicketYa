package com.ticketya.catalog.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SectorPricesRequest(@NotEmpty @Valid List<@NotNull PriceItem> prices) {

    public record PriceItem(
            @NotNull UUID sectorId,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @NotBlank String currency) {
    }
}
