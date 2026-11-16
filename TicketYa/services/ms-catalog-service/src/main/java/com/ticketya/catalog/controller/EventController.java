package com.ticketya.catalog.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketya.catalog.dto.request.CancelEventRequest;
import com.ticketya.catalog.dto.request.CreateEventRequest;
import com.ticketya.catalog.dto.request.EventSearchCriteria;
import com.ticketya.catalog.dto.request.SectorPricesRequest;
import com.ticketya.catalog.dto.request.UpdateEventRequest;
import com.ticketya.catalog.dto.response.EventResponse;
import com.ticketya.platform.web.PageResponse;
import com.ticketya.catalog.entity.EventStatus;
import com.ticketya.catalog.entity.EventType;
import com.ticketya.catalog.service.EventSearchService;
import com.ticketya.catalog.service.EventService;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.RequireRole;
import com.ticketya.platform.security.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Eventos")
public class EventController {

    private final EventService service;
    private final EventSearchService search;

    public EventController(EventService service, EventSearchService search) {
        this.service = service;
        this.search = search;
    }

    // ------------------------------------------------------------------ público

    @GetMapping
    @Operation(summary = "Buscar eventos",
            description = "Búsqueda multicriterio (AND). Solo PUBLISHED y SOLD_OUT. `sort=relevance` usa `q`; sin `q` equivale a fecha ascendente.")
    public PageResponse<EventResponse> search(
            @RequestParam(name = "type", required = false) EventType type,
            @RequestParam(name = "organizerId", required = false) UUID organizerId,
            @RequestParam(name = "participant", required = false) String participant,
            @RequestParam(name = "city", required = false) String city,
            @Parameter(description = "yyyy-MM-dd, inclusive")
            @RequestParam(name = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "yyyy-MM-dd, inclusive")
            @RequestParam(name = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(name = "q", required = false) String q,
            @Parameter(description = "date | price | relevance") @RequestParam(name = "sort", required = false) String sort,
            @Parameter(description = "asc | desc") @RequestParam(name = "order", required = false) String order,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Default 20, máx 100") @RequestParam(name = "size", defaultValue = "20") int size) {
        return search.search(new EventSearchCriteria(type, organizerId, participant, city, dateFrom, dateTo, q,
                sort, order, page, size));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Detalle de evento",
            description = "PUBLISHED/SOLD_OUT son públicos; los demás estados solo los ve el organizador dueño o un ADMIN (404 para el resto). No incluye disponibilidad.")
    public EventResponse get(@PathVariable("eventId") UUID eventId,
            @Parameter(hidden = true) Optional<Identity> user) {
        return service.obtenerVisible(user, eventId);
    }

    // ------------------------------------------------------------------ organizador

    @PostMapping
    @RequireRole(Role.ORGANIZER)
    @Operation(summary = "Crear evento (borrador)", description = "Queda en DRAFT asociado al organizador. `startsAt` debe ser futura.")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest req,
            @Parameter(hidden = true) Identity user) {
        EventResponse res = service.create(user, req);
        return ResponseEntity.created(URI.create("/api/events/" + res.id())).body(res);
    }

    @GetMapping("/mine")
    @RequireRole(Role.ORGANIZER)
    @Operation(summary = "Listar mis eventos (incluye borradores)")
    public PageResponse<EventResponse> mine(@Parameter(hidden = true) Identity user,
            @RequestParam(name = "status", required = false) EventStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Default 20, máx 100") @RequestParam(name = "size", defaultValue = "20") int size) {
        return service.listarMios(user, status, page, size);
    }

    @PutMapping("/{eventId}")
    @RequireRole(Role.ORGANIZER)
    @Operation(summary = "Modificar evento en borrador", description = "Solo el dueño y solo en DRAFT.")
    public EventResponse update(@PathVariable("eventId") UUID eventId, @Valid @RequestBody UpdateEventRequest req,
            @Parameter(hidden = true) Identity user) {
        return service.update(user, eventId, req);
    }

    @PutMapping("/{eventId}/sector-prices")
    @RequireRole(Role.ORGANIZER)
    @Operation(summary = "Definir precios por sector",
            description = "Reemplaza el conjunto de precios. Solo DRAFT y solo el dueño. Precios > 0 y sectores del lugar del evento.")
    public EventResponse setPrices(@PathVariable("eventId") UUID eventId, @Valid @RequestBody SectorPricesRequest req,
            @Parameter(hidden = true) Identity user) {
        return service.setPrices(user, eventId, req);
    }

    @PostMapping("/{eventId}/publish")
    @RequireRole(Role.ORGANIZER)
    @Operation(summary = "Publicar evento",
            description = "Requiere >=1 sector con precio y fecha futura. Inicializa el inventario antes de pasar a PUBLISHED (503 si inventory no responde; sigue DRAFT).")
    public EventResponse publish(@PathVariable("eventId") UUID eventId,
            @Parameter(hidden = true) Identity user) {
        return service.publish(user, eventId);
    }

    // ------------------------------------------------------------------ ADMIN

    @PostMapping("/{eventId}/suspend")
    @RequireRole(Role.ADMIN)
    @Operation(summary = "Suspender evento", description = "PUBLISHED/SOLD_OUT -> SUSPENDED. Sale de las búsquedas y no admite compras.")
    public EventResponse suspend(@PathVariable("eventId") UUID eventId) {
        return service.suspend(eventId);
    }

    @PostMapping("/{eventId}/resume")
    @RequireRole(Role.ADMIN)
    @Operation(summary = "Reactivar evento", description = "SUSPENDED -> PUBLISHED.")
    public EventResponse resume(@PathVariable("eventId") UUID eventId) {
        return service.resume(eventId);
    }

    @PostMapping("/{eventId}/cancel")
    @RequireRole(Role.ADMIN)
    @Operation(summary = "Cancelar evento",
            description = "Cancela (DRAFT, PUBLISHED, SOLD_OUT o SUSPENDED), cierra el inventario y dispara los reintegros en order-service.")
    public EventResponse cancel(@PathVariable("eventId") UUID eventId,
            @Valid @RequestBody(required = false) CancelEventRequest body) {
        return service.cancel(eventId, body == null ? null : body.reason());
    }
}
