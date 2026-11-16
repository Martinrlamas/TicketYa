package com.ticketya.catalog.client;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ticketya.catalog.client.dto.EventCancellationRequest;

@Component
class RestOrderClient implements OrderClient {

    private final RestClient http;

    RestOrderClient(@Qualifier("orderRestClient") RestClient http) {
        this.http = http;
    }

    @Override
    public void notifyEventCancelled(UUID eventId, String reason) {
        http.post().uri("/internal/events/{id}/cancellation", eventId)
                .body(new EventCancellationRequest(reason))
                .retrieve().toBodilessEntity();
    }
}
