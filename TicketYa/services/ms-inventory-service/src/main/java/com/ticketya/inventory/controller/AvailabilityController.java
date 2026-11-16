package com.ticketya.inventory.controller;

import com.ticketya.inventory.dto.response.AvailabilityResponse;
import com.ticketya.inventory.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Disponibilidad", description = "Consulta pública, nunca cacheada")
public class AvailabilityController {

    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/api/events/{eventId}/availability")
    @Operation(summary = "Disponibilidad actual del evento (PUBLIC)",
            description = "Responde Cache-Control: no-store. Estados de asiento: AVAILABLE, HELD, SOLD. 404 si el evento no tiene inventario.")
    public ResponseEntity<AvailabilityResponse> get(@PathVariable UUID eventId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.get(eventId));
    }
}
