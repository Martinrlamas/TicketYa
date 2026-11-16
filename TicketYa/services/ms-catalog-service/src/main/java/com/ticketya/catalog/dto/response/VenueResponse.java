package com.ticketya.catalog.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VenueResponse(UUID id, String name, String address, String city, BigDecimal latitude,
        BigDecimal longitude, List<SectorResponse> sectors) {
}
