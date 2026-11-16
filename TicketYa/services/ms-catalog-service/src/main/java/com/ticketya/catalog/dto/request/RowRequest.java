package com.ticketya.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RowRequest(
        @NotBlank @Size(max = 10) String rowLabel,
        @NotNull @Min(1) Integer seatCount) {
}
