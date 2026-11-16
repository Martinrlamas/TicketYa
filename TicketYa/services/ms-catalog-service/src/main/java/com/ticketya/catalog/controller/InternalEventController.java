package com.ticketya.catalog.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketya.catalog.dto.request.UpdateStatusRequest;
import com.ticketya.catalog.dto.response.InternalStatusResponse;
import com.ticketya.catalog.dto.response.SaleInfoResponse;
import com.ticketya.catalog.dto.response.EventResponse;
import com.ticketya.catalog.service.EventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Endpoints para otros servicios (X-Internal-Token). El gateway nunca los rutea. */
@RestController
@RequestMapping("/internal/events")
@Tag(name = "Interno")
public class InternalEventController {

    private final EventService service;

    public InternalEventController(EventService service) {
        this.service = service;
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Detalle de evento en cualquier estado (para ticket-service)",
            description = "No filtra por visibilidad: sirve para mostrar el nombre de eventos cancelados. **Roles:** INTERNAL")
    public EventResponse get(@PathVariable("eventId") UUID eventId) {
        return service.getAnyStatus(eventId);
    }

    @GetMapping("/{eventId}/sale-info")
    @Operation(summary = "Información de venta (para order-service)",
            description = "Si el evento es comprable y los tipos/precios por sector. **Roles:** INTERNAL")
    public SaleInfoResponse saleInfo(@PathVariable("eventId") UUID eventId) {
        return service.saleInfo(eventId);
    }

    @PutMapping("/{eventId}/status")
    @Operation(summary = "Actualizar estado SOLD_OUT/PUBLISHED (desde inventory)",
            description = "Solo admite SOLD_OUT <-> PUBLISHED; repetir el estado actual es idempotente. **Roles:** INTERNAL")
    public InternalStatusResponse updateStatus(@PathVariable("eventId") UUID eventId,
            @Valid @RequestBody UpdateStatusRequest req) {
        return service.applyInventoryStatus(eventId, req.status());
    }
}
