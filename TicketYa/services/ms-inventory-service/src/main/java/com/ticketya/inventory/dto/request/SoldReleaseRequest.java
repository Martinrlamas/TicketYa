package com.ticketya.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record SoldReleaseRequest(
        @NotNull UUID releaseId,
        @NotEmpty @Valid List<StockItemRequest> items) {}
