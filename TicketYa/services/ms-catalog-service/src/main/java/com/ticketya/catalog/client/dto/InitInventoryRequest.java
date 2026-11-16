package com.ticketya.catalog.client.dto;

import java.util.List;

public record InitInventoryRequest(List<InventorySectorDto> sectors) {
}
