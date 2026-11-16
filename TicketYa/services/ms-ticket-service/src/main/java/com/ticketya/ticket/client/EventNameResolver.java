package com.ticketya.ticket.client;

import com.ticketya.platform.http.PlatformRestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventNameResolver {

    private record EventWire(UUID id, String name) {}

    private final RestClient catalog;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();

    public EventNameResolver(PlatformRestClientFactory factory,
                             @Value("${ticketya.clients.catalog-url}") String url) {
        this.catalog = factory.createInternal(url);
    }

    /** Nombre por eventId; si catalog no responde, ese evento queda sin nombre (null). */
    public Map<UUID, String> namesFor(Collection<UUID> eventIds) {
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : new HashSet<>(eventIds)) {
            String name = cache.computeIfAbsent(id, this::fetchName);   // null no se guarda y se reintenta
            if (name != null) {
                names.put(id, name);
            }
        }
        return names;
    }

    private String fetchName(UUID eventId) {
        try {
            EventWire e = catalog.get().uri("/api/events/{id}", eventId).retrieve().body(EventWire.class);
            return e == null ? null : e.name();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
