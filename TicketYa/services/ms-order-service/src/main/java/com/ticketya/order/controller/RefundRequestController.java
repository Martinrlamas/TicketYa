package com.ticketya.order.controller;

import com.ticketya.order.dto.request.CreateRefundRequest;
import com.ticketya.order.dto.request.RejectRefundRequest;
import com.ticketya.platform.web.PageResponse;
import com.ticketya.order.dto.response.RefundRequestResponse;
import com.ticketya.order.entity.RefundRequestStatus;
import com.ticketya.order.service.RefundDecisionService;
import com.ticketya.order.service.RefundQueryService;
import com.ticketya.order.service.RequestRefundService;
import com.ticketya.platform.security.Identity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@RestController
@Tag(name = "Devoluciones")
public class RefundRequestController {

    private final RequestRefundService requestService;
    private final RefundDecisionService decisionService;
    private final RefundQueryService queryService;

    public RefundRequestController(RequestRefundService requestService, RefundDecisionService decisionService,
                                   RefundQueryService queryService) {
        this.requestService = requestService;
        this.decisionService = decisionService;
        this.queryService = queryService;
    }

    @PostMapping("/api/orders/{orderId}/items/{itemId}/refund-requests")
    @Operation(summary = "Solicitar devolución de una entrada (CUSTOMER)",
            description = "Hasta 72 h antes del evento (422 REFUND_WINDOW_CLOSED). Una sola solicitud activa por ítem (409).")
    public ResponseEntity<RefundRequestResponse> request(
            @Parameter(hidden = true) Identity identity,
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody(required = false) CreateRefundRequest body) {
        var response = requestService.request(identity, orderId, itemId, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/refund-requests/mine")
    @Operation(summary = "Listar mis solicitudes de devolución (CUSTOMER)")
    public PageResponse<RefundRequestResponse> mine(
            @Parameter(hidden = true) Identity identity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.listarMios(identity, page, size);
    }

    @GetMapping("/api/refund-requests")
    @Operation(summary = "Listar solicitudes de devolución (ADMIN)",
            description = "Filtro opcional por estado; ordenadas por antigüedad. Las FAILED quedan visibles para el ADMIN.")
    public PageResponse<RefundRequestResponse> list(
            @Parameter(hidden = true) Identity identity,
            @RequestParam(required = false) RefundRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.listAll(identity, status, page, size);
    }

    @PostMapping("/api/refund-requests/{refundRequestId}/approve")
    @Operation(summary = "Aprobar devolución (ADMIN)",
            description = "Deja la solicitud en PROCESSING; la saga (invalidar entrada, liberar lugar, reintegrar) se ejecuta y reintenta en segundo plano.")
    public RefundRequestResponse approve(
            @Parameter(hidden = true) Identity identity,
            @PathVariable UUID refundRequestId) {
        return decisionService.approve(identity, refundRequestId);
    }

    @PostMapping("/api/refund-requests/{refundRequestId}/reject")
    @Operation(summary = "Rechazar devolución (ADMIN)", description = "El ítem vuelve a ACTIVE.")
    public RefundRequestResponse reject(
            @Parameter(hidden = true) Identity identity,
            @PathVariable UUID refundRequestId,
            @Valid @RequestBody(required = false) RejectRefundRequest body) {
        return decisionService.reject(identity, refundRequestId, body == null ? null : body.reason());
    }
}
