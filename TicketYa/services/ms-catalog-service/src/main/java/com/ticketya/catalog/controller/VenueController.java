package com.ticketya.catalog.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketya.catalog.dto.request.CreateSectorRequest;
import com.ticketya.catalog.dto.request.CreateVenueRequest;
import com.ticketya.catalog.dto.response.SectorResponse;
import com.ticketya.catalog.dto.response.VenueResponse;
import com.ticketya.catalog.service.VenueService;
import com.ticketya.platform.security.RequireRole;
import com.ticketya.platform.security.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/venues")
@Tag(name = "Lugares")
public class VenueController {

    private final VenueService service;

    public VenueController(VenueService service) {
        this.service = service;
    }

    @PostMapping
    @RequireRole({Role.ADMIN, Role.ORGANIZER})
    @Operation(summary = "Crear lugar", description = "Crea un lugar sin sectores. **Roles:** ADMIN, ORGANIZER")
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest req) {
        VenueResponse res = service.create(req);
        return ResponseEntity.created(URI.create("/api/venues/" + res.id())).body(res);
    }

    @GetMapping("/{venueId}")
    @Operation(summary = "Consultar lugar con sectores", description = "Público.")
    public VenueResponse get(@PathVariable("venueId") UUID venueId) {
        return service.get(venueId);
    }

    @PostMapping("/{venueId}/sectors")
    @RequireRole({Role.ADMIN, Role.ORGANIZER})
    @Operation(summary = "Agregar sector a un lugar",
            description = "NUMBERED exige rows (>=1 fila con seatCount>0) y NO admite capacity. "
                    + "GENERAL exige capacity>0 y NO admite rows. **Roles:** ADMIN, ORGANIZER")
    public ResponseEntity<SectorResponse> agregarSector(@PathVariable("venueId") UUID venueId,
            @Valid @RequestBody CreateSectorRequest req) {
        SectorResponse res = service.agregarSector(venueId, req);
        return ResponseEntity.created(URI.create("/api/venues/" + venueId)).body(res);
    }
}
