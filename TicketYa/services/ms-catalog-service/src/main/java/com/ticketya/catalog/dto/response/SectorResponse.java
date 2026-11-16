package com.ticketya.catalog.dto.response;

import java.util.List;
import java.util.UUID;

import com.ticketya.catalog.entity.SectorType;

public record SectorResponse(UUID id, String name, SectorType type, int capacity, List<RowResponse> rows) {
}
