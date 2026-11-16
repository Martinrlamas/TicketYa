package com.ticketya.ticket.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Pedido de emisión de order-service. DTO propio: no se comparten clases con otros servicios. */
public record IssueTicketsRequest(
        @NotNull UUID orderId,
        @NotNull UUID eventId,
        @NotNull UUID customerId,
        @NotEmpty @Valid List<Item> items) {

    public record Item(
            @NotNull UUID orderItemId,
            @NotNull UUID sectorId,
            UUID seatId,                                   // null en zona general
            @NotBlank @Size(max = 120) String seatLabel) {}
}
