package com.ticketya.payment.service;

import com.ticketya.platform.idempotency.IdempotencyLock;
import com.ticketya.payment.dto.request.CreatePaymentRequest;
import com.ticketya.payment.dto.response.PaymentResponse;
import com.ticketya.payment.entity.Payment;
import com.ticketya.payment.entity.PaymentStatus;
import com.ticketya.payment.exception.PaymentException;
import com.ticketya.payment.gateway.GatewayChargeRequest;
import com.ticketya.payment.gateway.GatewayResult;
import com.ticketya.payment.gateway.PaymentGateway;
import com.ticketya.payment.mapper.PaymentMapper;
import com.ticketya.payment.repository.PaymentRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepo;
    private final PaymentGateway gateway;
    private final RequestHasher hasher;
    private final IdempotencyLock lock;
    private final PaymentMapper mapper;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepo, PaymentGateway gateway, RequestHasher hasher,
                          IdempotencyLock lock, PaymentMapper mapper, Clock clock) {
        this.paymentRepo = paymentRepo;
        this.gateway = gateway;
        this.hasher = hasher;
        this.lock = lock;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Procesa un pago de forma idempotente. Un rechazo se persiste (auditoría) y se responde normalmente:
     * el 402 hacia el cliente lo decide order-service. Si la pasarela falla técnicamente, la excepción
     * revierte la transacción: no queda nada guardado y se puede reintentar con la misma clave.
     */
    @Transactional
    public PaymentResponse process(CreatePaymentRequest req) {
        String hash = hasher.hash(req);
        lock.acquire("payment", req.idempotencyKey());          // los demás hilos con la misma clave esperan acá

        Optional<Payment> existing = paymentRepo.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw PaymentException.claveIdempotenciaReutilizada();
            }
            return mapper.toResponse(existing.get());           // mismo resultado, la pasarela NO se invoca
        }

        GatewayResult result = gateway.charge(new GatewayChargeRequest(
                req.idempotencyKey(), req.amount(), req.currency(), req.token()));

        Payment payment = paymentRepo.save(Payment.create(
                req.orderId(), req.customerId(), RequestHasher.normalize(req.amount()), req.currency(),
                req.method(), req.token(), req.last4(),
                result.approved() ? PaymentStatus.APPROVED : PaymentStatus.REJECTED,
                result.gatewayRef(), req.idempotencyKey(), hash, clock.instant()));

        // Nunca se loguea el medio de pago: solo ids y resultado
        log.info("payment processed paymentId={} orderId={} status={}", payment.getId(), payment.getOrderId(), payment.getStatus());
        return mapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId) {
        return paymentRepo.findById(paymentId)
                .map(mapper::toResponse)
                .orElseThrow(() -> PaymentException.noEncontrado("Pago inexistente: " + paymentId));
    }

    /** Reconciliación: order-service consulta por orden cuando el resultado de un pago quedó incierto. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> findByOrder(UUID orderId) {
        return paymentRepo.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(mapper::toResponse).toList();
    }
}
