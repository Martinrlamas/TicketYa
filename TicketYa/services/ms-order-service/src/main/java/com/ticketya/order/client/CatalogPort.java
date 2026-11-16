package com.ticketya.order.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Puerto hacia catalog-service. Los tipos son propios de order (anti-corruption layer). */
public interface CatalogPort {

    /** @throws com.ticketya.order.exception.OrderException NOT_FOUND (404) si el evento no existe; 503 si catalog no responde. */
    SaleInfo getSaleInfo(UUID eventId);

    record SaleInfo(UUID eventId, String status, boolean purchasable, Instant startsAt, List<SectorSale> sectors) {

        public SectorSale sector(UUID sectorId) {
            return sectors.stream().filter(s -> s.sectorId().equals(sectorId)).findFirst().orElse(null);
        }
    }

    record SectorSale(UUID sectorId, String name, String type, BigDecimal price, String currency) {

        public boolean isNumbered() {
            return "NUMBERED".equals(type);
        }
    }
}
