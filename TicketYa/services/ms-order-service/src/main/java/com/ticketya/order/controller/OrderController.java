package com.ticketya.order.controller;

import com.ticketya.order.dto.request.CreateOrderRequest;
import com.ticketya.order.dto.request.PayOrderRequest;
import com.ticketya.order.dto.response.OrderResponse;
import com.ticketya.platform.web.PageResponse;
import com.ticketya.order.entity.OrderStatus;
import com.ticketya.order.service.CancelOrderService;
import com.ticketya.order.service.CreateOrderService;
import com.ticketya.order.service.OrderQueryService;
import com.ticketya.order.service.PayOrderService;
import com.ticketya.platform.security.Identity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Órdenes")
public class OrderController {

    private final CreateOrderService createService;
    private final PayOrderService payService;
    private final CancelOrderService cancelService;
    private final OrderQueryService queryService;

    public OrderController(CreateOrderService createService, PayOrderService payService,
                           CancelOrderService cancelService, OrderQueryService queryService) {
        this.createService = createService;
        this.payService = payService;
        this.cancelService = cancelService;
        this.queryService = queryService;
    }

    @PostMapping
    @Operation(summary = "Crear orden (CUSTOMER)",
            description = "Requiere Idempotency-Key. Crea el hold y devuelve 201; un reintento con la misma clave y "
                    + "payload devuelve 200 con la misma orden; con otro payload, 422.")
    public ResponseEntity<OrderResponse> create(
            @Parameter(hidden = true) Identity identity,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        var result = createService.create(identity, idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }

    @PostMapping("/{orderId}/pay")
    @Operation(summary = "Pagar orden (CUSTOMER)",
            description = "Requiere Idempotency-Key. 402 si el pago es rechazado, 410 si la reserva venció.")
    public OrderResponse pay(
            @Parameter(hidden = true) Identity identity,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID orderId,
            @Valid @RequestBody PayOrderRequest request) {
        return payService.pay(identity, orderId, idempotencyKey, request);
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "Cancelar orden pendiente (CUSTOMER)")
    public OrderResponse cancel(
            @Parameter(hidden = true) Identity identity,
            @PathVariable UUID orderId) {
        return cancelService.cancel(identity, orderId);
    }

    @GetMapping
    @Operation(summary = "Listar mis órdenes (CUSTOMER)")
    public PageResponse<OrderResponse> list(
            @Parameter(hidden = true) Identity identity,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.listarMios(identity, status, page, size);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Detalle de una orden (CUSTOMER dueño, ADMIN)")
    public OrderResponse get(
            @Parameter(hidden = true) Identity identity,
            @PathVariable UUID orderId) {
        return queryService.obtenerUno(identity, orderId);
    }
}
