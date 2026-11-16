package com.ticketya.catalog.client;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ticketya.catalog.client.dto.InitInventoryRequest;
import com.ticketya.catalog.client.dto.InventorySectorDto;

/**
 * Timeouts, X-Internal-Token, correlation-id y traducción de errores (timeout/5xx -> UPSTREAM_UNAVAILABLE)
 * los resuelve el RestClient creado con PlatformRestClientFactory del starter.
 */
@Component
class RestInventoryClient implements InventoryClient {

    private final RestClient http;

    RestInventoryClient(@Qualifier("inventoryRestClient") RestClient http) {
        this.http = http;
    }

    @Override
    public void initialize(UUID eventId, List<InventorySectorDto> sectors) {
        http.post().uri("/internal/events/{id}/inventory", eventId)
                .body(new InitInventoryRequest(sectors))
                .retrieve().toBodilessEntity();
    }

    @Override
    public void close(UUID eventId) {
        http.post().uri("/internal/events/{id}/close", eventId)
                .retrieve().toBodilessEntity();
    }
}
