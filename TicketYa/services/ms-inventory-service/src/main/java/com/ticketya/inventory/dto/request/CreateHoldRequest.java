package com.ticketya.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record CreateHoldRequest(
        @NotNull UUID eventId,
        @NotNull UUID orderId,
        @Positive Integer ttlSeconds,
        @NotEmpty @Valid List<StockItemRequest> items) {}
