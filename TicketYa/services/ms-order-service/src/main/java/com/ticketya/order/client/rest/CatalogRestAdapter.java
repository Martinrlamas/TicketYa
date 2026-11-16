package com.ticketya.order.client.rest;

import com.ticketya.order.client.CatalogPort;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogRestAdapter implements CatalogPort {

    // DTOs de cable propios: no se comparten clases con catalog-service
    record SaleInfoWire(UUID eventId, String status, boolean purchasable, OffsetDateTime startsAt, List<SectorWire> sectors) {}

    record SectorWire(UUID sectorId, String name, String type, BigDecimal price, String currency) {}

    private final RestClient client;

    public CatalogRestAdapter(@Qualifier("catalogRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public SaleInfo getSaleInfo(UUID eventId) {
        SaleInfoWire wire = UpstreamErrors.call("catalog-service", () -> client.get()
                .uri("/internal/events/{id}/sale-info", eventId)
                .retrieve()
                .body(SaleInfoWire.class));
        List<SectorWire> sectors = wire.sectors() != null ? wire.sectors() : List.of();
        return new SaleInfo(wire.eventId(), wire.status(), wire.purchasable(), wire.startsAt().toInstant(),
                sectors.stream().map(s -> new SectorSale(s.sectorId(), s.name(), s.type(), s.price(), s.currency())).toList());
    }
}
