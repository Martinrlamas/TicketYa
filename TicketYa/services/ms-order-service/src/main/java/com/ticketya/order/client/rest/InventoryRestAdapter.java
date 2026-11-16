package com.ticketya.order.client.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ticketya.order.client.InventoryPort;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryRestAdapter implements InventoryPort {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ItemWire(UUID sectorId, List<UUID> seatIds, Integer quantity) {}

    record HoldRequestWire(UUID eventId, UUID orderId, int ttlSeconds, List<ItemWire> items) {}

    record HoldResponseWire(UUID holdId, UUID orderId, String status, OffsetDateTime expiresAt, List<HeldSeatWire> seats) {}

    record HeldSeatWire(UUID seatId, UUID sectorId, String label) {}

    record SoldReleaseWire(UUID releaseId, List<ItemWire> items) {}

    private final RestClient client;

    public InventoryRestAdapter(@Qualifier("inventoryRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public HoldInfo createHold(UUID eventId, UUID orderId, int ttlSeconds, List<StockItem> items) {
        var body = new HoldRequestWire(eventId, orderId, ttlSeconds, wire(items));
        HoldResponseWire res = UpstreamErrors.call("inventory-service", () -> client.post()
                .uri("/internal/holds")
                .body(body)
                .retrieve()
                .body(HoldResponseWire.class));
        Map<UUID, String> labels = new LinkedHashMap<>();
        if (res.seats() != null) {
            res.seats().forEach(s -> labels.put(s.seatId(), s.label()));
        }
        return new HoldInfo(res.holdId(), res.expiresAt().toInstant(), labels);
    }

    @Override
    public void confirmHold(UUID holdId) {
        UpstreamErrors.run("inventory-service", () -> client.post()
                .uri("/internal/holds/{id}/confirm", holdId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void releaseHold(UUID holdId) {
        UpstreamErrors.run("inventory-service", () -> client.delete()
                .uri("/internal/holds/{id}", holdId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void releaseSold(UUID eventId, UUID releaseId, List<StockItem> items) {
        var body = new SoldReleaseWire(releaseId, wire(items));
        UpstreamErrors.run("inventory-service", () -> client.post()
                .uri("/internal/events/{id}/sold/release", eventId)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void closeEvent(UUID eventId) {
        UpstreamErrors.run("inventory-service", () -> client.post()
                .uri("/internal/events/{id}/close", eventId)
                .retrieve()
                .toBodilessEntity());
    }

    private static List<ItemWire> wire(List<StockItem> items) {
        return items.stream().map(i -> new ItemWire(i.sectorId(), i.seatIds(), i.quantity())).toList();
    }
}
