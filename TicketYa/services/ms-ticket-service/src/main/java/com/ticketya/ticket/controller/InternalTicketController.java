package com.ticketya.ticket.controller;

import com.ticketya.ticket.dto.request.InvalidateTicketRequest;
import com.ticketya.ticket.dto.request.IssueTicketsRequest;
import com.ticketya.ticket.dto.response.EventInvalidationResponse;
import com.ticketya.ticket.dto.response.IssueTicketsResponse;
import com.ticketya.ticket.dto.response.TicketResponse;
import com.ticketya.ticket.service.TicketInvalidationService;
import com.ticketya.ticket.service.TicketIssueService;
import com.ticketya.ticket.service.TicketQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints internos: X-Internal-Token lo exige el starter y el gateway nunca los rutea. */
@RestController
@Tag(name = "Entradas (interno)")
public class InternalTicketController {

    private final TicketIssueService issueService;
    private final TicketInvalidationService invalidationService;
    private final TicketQueryService queryService;

    public InternalTicketController(TicketIssueService issueService, TicketInvalidationService invalidationService,
                                    TicketQueryService queryService) {
        this.issueService = issueService;
        this.invalidationService = invalidationService;
        this.queryService = queryService;
    }

    @GetMapping("/internal/tickets")
    @Operation(summary = "Entradas de una orden", description = "Devuelve las entradas ya emitidas de la orden (lista vacía si no hay).")
    public IssueTicketsResponse listarPorOrden(@RequestParam UUID orderId) {
        return queryService.listarPorOrden(orderId);
    }

    @PostMapping("/internal/tickets")
    @Operation(summary = "Emitir entradas (idempotente por orderItemId)",
            description = "201 si se creó alguna entrada; 200 si todas ya estaban emitidas (devuelve las existentes).")
    public ResponseEntity<IssueTicketsResponse> issue(@Valid @RequestBody IssueTicketsRequest request) {
        var result = issueService.issue(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }

    @PostMapping("/internal/tickets/{ticketId}/invalidate")
    @Operation(summary = "Invalidar entrada (idempotente)", description = "REFUNDED o CANCELLED según reason. 409 si está USED.")
    public TicketResponse invalidate(@PathVariable UUID ticketId, @Valid @RequestBody InvalidateTicketRequest request) {
        return invalidationService.invalidate(ticketId, request.reason());
    }

    @PostMapping("/internal/events/{eventId}/tickets/invalidate")
    @Operation(summary = "Invalidar todas las entradas VALID de un evento (cancelación)")
    public EventInvalidationResponse invalidarPorEvento(@PathVariable UUID eventId,
                                                       @Valid @RequestBody InvalidateTicketRequest request) {
        return invalidationService.invalidarPorEvento(eventId, request.reason());
    }
}
