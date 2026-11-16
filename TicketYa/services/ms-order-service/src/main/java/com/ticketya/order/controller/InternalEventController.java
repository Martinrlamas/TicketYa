package com.ticketya.order.controller;

import com.ticketya.order.dto.request.CancelEventRequest;
import com.ticketya.order.dto.response.CancelEventResponse;
import com.ticketya.order.service.CancelEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint interno: X-Internal-Token lo exige el starter y el gateway nunca lo rutea. */
@RestController
@Tag(name = "Cancelación de eventos (interno)")
public class InternalEventController {

    private final CancelEventService service;

    public InternalEventController(CancelEventService service) {
        this.service = service;
    }

    @PostMapping("/internal/events/{eventId}/cancellation")
    @Operation(summary = "Notificar cancelación de evento (desde catalog)",
            description = "Idempotente por eventId: 202 la primera vez, 200 si ya estaba registrada.")
    public ResponseEntity<CancelEventResponse> cancel(@PathVariable UUID eventId,
                                                     @Valid @RequestBody(required = false) CancelEventRequest body) {
        var result = service.cancel(eventId, body == null ? null : body.reason());
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result.body());
    }
}
