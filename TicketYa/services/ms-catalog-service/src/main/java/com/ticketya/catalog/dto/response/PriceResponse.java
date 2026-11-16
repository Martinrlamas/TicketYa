package com.ticketya.catalog.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceResponse(UUID sectorId, String sectorName, BigDecimal price, String currency) {
}
