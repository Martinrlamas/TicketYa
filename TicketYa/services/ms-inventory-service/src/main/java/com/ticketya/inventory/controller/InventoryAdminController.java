package com.ticketya.inventory.controller;

import com.ticketya.inventory.dto.request.InitInventoryRequest;
import com.ticketya.inventory.dto.request.SoldReleaseRequest;
import com.ticketya.inventory.dto.response.InitInventoryResponse;
import com.ticketya.inventory.dto.response.SoldReleaseResponse;
import com.ticketya.inventory.service.InventoryCloseService;
import com.ticketya.inventory.service.InventoryInitService;
import com.ticketya.inventory.service.SoldReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints internos por evento (X-Internal-Token lo exige el starter). Nunca se rutean por el gateway. */
@RestController
@RequestMapping("/internal/events/{eventId}")
@Tag(name = "Inventario (interno)")
public class InventoryAdminController {

    private final InventoryInitService initService;
    private final InventoryCloseService closeService;
    private final SoldReleaseService soldReleaseService;

    public InventoryAdminController(InventoryInitService initService, InventoryCloseService closeService,
                                    SoldReleaseService soldReleaseService) {
        this.initService = initService;
        this.closeService = closeService;
        this.soldReleaseService = soldReleaseService;
    }

    @PostMapping("/inventory")
    @Operation(summary = "Inicializar inventario (idempotente)", description = "201 si lo creó, 200 si ya estaba inicializado.")
    public ResponseEntity<InitInventoryResponse> init(@PathVariable UUID eventId,
                                                      @Valid @RequestBody InitInventoryRequest request) {
        var result = initService.init(eventId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }

    @PostMapping("/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar inventario: no admite nuevos holds (idempotente)")
    public void close(@PathVariable UUID eventId) {
        closeService.close(eventId);
    }

    @PostMapping("/sold/release")
    @Operation(summary = "Devolver lugares vendidos al stock (idempotente por releaseId)")
    public SoldReleaseResponse soldRelease(@PathVariable UUID eventId,
                                           @Valid @RequestBody SoldReleaseRequest request) {
        return soldReleaseService.release(eventId, request);
    }
}
