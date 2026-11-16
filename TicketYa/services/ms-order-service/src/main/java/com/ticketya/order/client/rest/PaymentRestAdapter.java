package com.ticketya.order.client.rest;

import com.ticketya.order.client.PaymentPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentRestAdapter implements PaymentPort {

    record ChargeWire(UUID orderId, UUID customerId, BigDecimal amount, String currency, String method,
                      String token, String last4, String idempotencyKey) {

        @Override
        public String toString() {
            return "ChargeWire[orderId=" + orderId + "]";
        }
    }

    record PaymentWire(UUID paymentId, UUID orderId, String status, String method, String last4) {}

    record RefundWire(BigDecimal amount, String reason, String idempotencyKey) {}

    private final RestClient client;

    public PaymentRestAdapter(@Qualifier("paymentRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public PaymentInfo charge(ChargeCommand c) {
        var body = new ChargeWire(c.orderId(), c.customerId(), c.amount(), c.currency(), c.method(),
                c.token(), c.last4(), c.idempotencyKey());
        PaymentWire res = UpstreamErrors.call("payment-service", () -> client.post()
                .uri("/internal/payments")
                .body(body)
                .retrieve()
                .body(PaymentWire.class));
        return toInfo(res);
    }

    @Override
    public List<PaymentInfo> findByOrder(UUID orderId) {
        List<PaymentWire> res = UpstreamErrors.call("payment-service", () -> client.get()
                .uri(b -> b.path("/internal/payments").queryParam("orderId", orderId).build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<PaymentWire>>() {}));
        return res == null ? List.of() : res.stream().map(PaymentRestAdapter::toInfo).toList();
    }

    @Override
    public void refund(UUID paymentId, BigDecimal amount, String reason, String idempotencyKey) {
        var body = new RefundWire(amount, reason, idempotencyKey);
        UpstreamErrors.run("payment-service", () -> client.post()
                .uri("/internal/payments/{id}/refunds", paymentId)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    private static PaymentInfo toInfo(PaymentWire w) {
        return new PaymentInfo(w.paymentId(), w.orderId(), w.status(), w.method(), w.last4());
    }
}
