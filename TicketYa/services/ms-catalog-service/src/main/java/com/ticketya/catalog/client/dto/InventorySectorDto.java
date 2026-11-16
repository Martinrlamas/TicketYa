package com.ticketya.catalog.client.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** DTO propio de catalog (no se comparte código con inventory-service). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventorySectorDto(UUID sectorId, String type, List<InventoryRowDto> rows, Integer capacity) {
}
