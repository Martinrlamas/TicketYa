package com.ticketya.payment.service;

import com.ticketya.platform.idempotency.IdempotencyLock;
import com.ticketya.payment.dto.request.CreateRefundRequest;
import com.ticketya.payment.dto.response.RefundResponse;
import com.ticketya.payment.entity.Payment;
import com.ticketya.payment.entity.Refund;
import com.ticketya.payment.entity.RefundStatus;
import com.ticketya.payment.exception.PaymentException;
import com.ticketya.payment.gateway.GatewayRefundRequest;
import com.ticketya.payment.gateway.GatewayResult;
import com.ticketya.payment.gateway.PaymentGateway;
import com.ticketya.payment.mapper.PaymentMapper;
import com.ticketya.payment.repository.PaymentRepository;
import com.ticketya.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {

    public record RefundResult(RefundResponse body, boolean created) {}

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentRepository paymentRepo;
    private final RefundRepository refundRepo;
    private final PaymentGateway gateway;
    private final IdempotencyLock lock;
    private final PaymentMapper mapper;
    private final Clock clock;

    public RefundService(PaymentRepository paymentRepo, RefundRepository refundRepo, PaymentGateway gateway,
                         IdempotencyLock lock, PaymentMapper mapper, Clock clock) {
        this.paymentRepo = paymentRepo;
        this.refundRepo = refundRepo;
        this.gateway = gateway;
        this.lock = lock;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public RefundResult refund(UUID paymentId, CreateRefundRequest req) {
        lock.acquire("refund", req.idempotencyKey());

        // Idempotencia: misma clave => el reintegro original; otra combinación pago/monto => 422
        Optional<Refund> previous = refundRepo.findByIdempotencyKey(req.idempotencyKey());
        if (previous.isPresent()) {
            Refund r = previous.get();
            if (!r.getPaymentId().equals(paymentId) || r.getAmount().compareTo(req.amount()) != 0) {
                throw PaymentException.claveIdempotenciaReutilizada();
            }
            return new RefundResult(mapper.toResponse(r), false);
        }

        BigDecimal amount = RequestHasher.normalize(req.amount());

        // UPDATE condicional atómico: la suma de reintegros nunca supera lo pagado, ni con reintegros concurrentes
        if (paymentRepo.addRefunded(paymentId, amount) == 0) {
            Payment payment = paymentRepo.findById(paymentId)
                    .orElseThrow(() -> PaymentException.noEncontrado("Pago inexistente: " + paymentId));
            if (!payment.isApproved()) {
                throw PaymentException.estadoInvalido("El pago está " + payment.getStatus());
            }
            throw PaymentException.reintegroExcedido("El reintegro de " + amount + " supera el saldo reintegrable de "
                    + payment.getAmount().subtract(payment.getRefundedAmount()));
        }

        Payment payment = paymentRepo.findById(paymentId).orElseThrow();
        GatewayResult result = gateway.refund(new GatewayRefundRequest(req.idempotencyKey(), payment.getGatewayRef(), amount));

        RefundStatus status = result.approved() ? RefundStatus.APPROVED : RefundStatus.REJECTED;
        if (status == RefundStatus.REJECTED) {
            paymentRepo.subtractRefunded(paymentId, amount);     // el rechazo no consume saldo
        }
        Refund saved = refundRepo.save(Refund.create(paymentId, amount, status, req.reason(), req.idempotencyKey(), clock.instant()));
        log.info("refund processed refundId={} paymentId={} status={}", saved.getId(), paymentId, status);
        return new RefundResult(mapper.toResponse(saved), true);
    }
}
