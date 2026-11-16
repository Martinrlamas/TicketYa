package com.ticketya.ticket.controller;

import com.ticketya.platform.web.PageResponse;
import com.ticketya.ticket.dto.response.TicketResponse;
import com.ticketya.ticket.entity.TicketStatus;
import com.ticketya.ticket.service.TicketQueryService;
import com.ticketya.platform.security.Identity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints públicos (pasan por el gateway, que reenvía X-User-Id y X-User-Role). */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Entradas")
public class TicketController {

    private final TicketQueryService queryService;

    public TicketController(TicketQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Listar mis entradas (CUSTOMER)",
            description = "Solo las del usuario autenticado. Filtros opcionales eventId y status. size por defecto 20, máximo 100.")
    public PageResponse<TicketResponse> list(
            @Parameter(hidden = true) Identity identity,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.listarMios(identity, eventId, status, page, size);
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "Detalle de una entrada (CUSTOMER, ADMIN)",
            description = "Un CUSTOMER solo accede a sus entradas: la de otro responde 404.")
    public TicketResponse get(
            @Parameter(hidden = true) Identity identity,
            @PathVariable UUID ticketId) {
        return queryService.obtenerUno(identity, ticketId);
    }
}
