package com.ticketya.inventory.client;

import com.ticketya.inventory.exception.InventoryException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Adaptador REST hacia catalog. Usa un DTO propio (anti-corruption layer): no se comparten clases con otros servicios. */
@Component
public class CatalogEventStatusNotifier implements EventStatusNotifier {

    private static final Logger log = LoggerFactory.getLogger(CatalogEventStatusNotifier.class);

    private final RestClient catalogRestClient;

    public CatalogEventStatusNotifier(RestClient catalogRestClient) {
        this.catalogRestClient = catalogRestClient;
    }

    @Override
    public void notifyStatus(UUID eventId, CatalogEventStatus status) {
        try {
            catalogRestClient.put()
                    .uri("/internal/events/{id}/status", eventId)
                    .body(Map.of("status", status.name()))
                    .retrieve()
                    .toBodilessEntity();
            log.info("catalog notificado eventId={} status={}", eventId, status);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                // catalog ya estaba en ese estado o la transición no aplica (ej. evento cancelado): no es un error
                log.info("catalog rechazó transición eventId={} status={} (409), se ignora", eventId, status);
                return;
            }
            throw InventoryException.servicioNoDisponible("catalog respondió " + ex.getStatusCode().value());
        } catch (RuntimeException ex) { // timeouts / conexión / 5xx (el cliente del starter los lanza como PlatformException)
            throw InventoryException.servicioNoDisponible("catalog no disponible: " + ex.getClass().getSimpleName());
        }
    }
}
