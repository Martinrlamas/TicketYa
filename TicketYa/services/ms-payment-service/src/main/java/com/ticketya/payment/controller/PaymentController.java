package com.ticketya.payment.controller;

import com.ticketya.payment.dto.request.CreatePaymentRequest;
import com.ticketya.payment.dto.request.CreateRefundRequest;
import com.ticketya.payment.dto.response.PaymentResponse;
import com.ticketya.payment.dto.response.RefundResponse;
import com.ticketya.payment.service.PaymentService;
import com.ticketya.payment.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sin rutas públicas: todo es /internal/** (X-Internal-Token lo exige el starter y el gateway no lo rutea). */
@RestController
@RequestMapping("/internal/payments")
@Tag(name = "Pagos (interno)")
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    public PaymentController(PaymentService paymentService, RefundService refundService) {
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @PostMapping
    @Operation(summary = "Procesar pago (idempotente)",
            description = "Siempre 200: un rechazo se persiste y responde status=REJECTED (el 402 lo decide order-service). "
                    + "Misma clave y datos => mismo resultado; misma clave y otros datos => 422 IDEMPOTENCY_KEY_REUSED.")
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.process(request);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Consultar pago")
    public PaymentResponse get(@PathVariable UUID paymentId) {
        return paymentService.get(paymentId);
    }

    @GetMapping
    @Operation(summary = "Buscar pagos por orden (reconciliación)", description = "Lista vacía si la orden no tiene pagos.")
    public List<PaymentResponse> byOrder(@RequestParam UUID orderId) {
        return paymentService.findByOrder(orderId);
    }

    @PostMapping("/{paymentId}/refunds")
    @Operation(summary = "Reintegrar total o parcialmente (idempotente)",
            description = "201 si lo registró; 200 si es un reintento con la misma clave. 409 si el pago no está APPROVED; 422 si excede lo pagado.")
    public ResponseEntity<RefundResponse> refund(@PathVariable UUID paymentId,
                                                 @Valid @RequestBody CreateRefundRequest request) {
        var result = refundService.refund(paymentId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }
}
