package com.ticketya.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ticketya.payment.AbstractPostgresTest;
import com.ticketya.payment.dto.request.CreatePaymentRequest;
import com.ticketya.payment.dto.response.PaymentResponse;
import com.ticketya.payment.entity.PaymentMethod;
import com.ticketya.payment.entity.PaymentStatus;
import com.ticketya.payment.exception.PaymentException;
import com.ticketya.payment.gateway.GatewayChargeRequest;
import com.ticketya.payment.gateway.GatewayUnavailableException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class PaymentServiceTest extends AbstractPostgresTest {

    @Autowired PaymentService paymentService;

    private static CreatePaymentRequest request(UUID orderId, String amount, String token, String key) {
        return new CreatePaymentRequest(orderId, uuid(), money(amount), "ARS", PaymentMethod.CARD, token, "4242", key);
    }

    private int paymentRows(String key) {
        return jdbc.queryForObject("select count(*) from payment where idempotency_key = ?", Integer.class, key);
    }

    // ---------- 2.1 aprobado / rechazado / montos inválidos ----------

    @Test
    void approvedPaymentReturnsGatewayRef() {
        PaymentResponse res = paymentService.process(request(uuid(), "90000.00", "tok_approved", "k-" + uuid()));

        assertThat(res.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(res.gatewayRef()).startsWith("mock-");
        assertThat(res.amount()).isEqualByComparingTo("90000.00");
        assertThat(res.refundedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void rejectedPaymentIsPersistedAndReturnedAsRejected() {
        String key = "k-" + uuid();

        PaymentResponse res = paymentService.process(request(uuid(), "100.00", "tok_rejected", key));

        assertThat(res.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(paymentRows(key)).isEqualTo(1);       // se guarda para auditoría
    }

    @Test
    void zeroOrNegativeAmountFailsBeanValidation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        for (String amount : new String[] {"0", "0.00", "-5.00"}) {
            var violations = validator.validate(request(uuid(), amount, "tok_approved", "k"));
            assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("amount");
        }
        assertThat(validator.validate(request(uuid(), "0.01", "tok_approved", "k"))).isEmpty();
    }

    // ---------- 2.2 idempotencia ----------

    @Test
    void sameKeyAndDataReturnsOriginalWithoutChargingAgain() {
        UUID order = uuid();
        String key = "pay-" + order;

        PaymentResponse first = paymentService.process(request(order, "5000.00", "tok_approved", key));
        PaymentResponse retry = paymentService.process(request(order, "5000.0", "tok_approved", key)); // 5000.0 == 5000.00

        assertThat(retry.paymentId()).isEqualTo(first.paymentId());
        assertThat(retry.status()).isEqualTo(first.status());
        verify(gateway, times(1)).charge(any(GatewayChargeRequest.class));
        assertThat(paymentRows(key)).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentAmountOrOrderIsRejected() {
        UUID order = uuid();
        String key = "pay-" + order;
        paymentService.process(request(order, "5000.00", "tok_approved", key));

        assertThatThrownBy(() -> paymentService.process(request(order, "5001.00", "tok_approved", key)))
                .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThatThrownBy(() -> paymentService.process(request(uuid(), "5000.00", "tok_approved", key)))
                .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    // ---------- 2.3 concurrencia ----------

    @Test
    void twentyConcurrentRequestsWithSameKeyCreateOnePayment() throws Exception {
        UUID order = uuid();
        String key = "pay-" + order;
        Map<UUID, Boolean> paymentIds = new ConcurrentHashMap<>();

        Map<String, Integer> result = ConcurrencyHelper.race(20,
                i -> paymentIds.put(paymentService.process(request(order, "7000.00", "tok_approved", key)).paymentId(), true));

        assertThat(result).containsOnlyKeys("OK").containsEntry("OK", 20);
        assertThat(paymentIds).hasSize(1);                          // todos recibieron el mismo paymentId
        assertThat(paymentRows(key)).isEqualTo(1);
        verify(gateway, times(1)).charge(any(GatewayChargeRequest.class));   // la pasarela cobró una sola vez
    }

    // ---------- pasarela mock ----------

    @Test
    void flakyTokenFailsFirstAttemptWithoutPersistingAndApprovesOnRetry() {
        UUID order = uuid();
        String key = "pay-" + order;

        assertThatThrownBy(() -> paymentService.process(request(order, "100.00", "tok_flaky", key)))
                .isInstanceOf(GatewayUnavailableException.class)
                .extracting("code").isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(paymentRows(key)).isZero();                       // el rollback no dejó nada

        PaymentResponse retry = paymentService.process(request(order, "100.00", "tok_flaky", key));
        assertThat(retry.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void timeoutTokenSleepsButStillPersistsSoOrderCanReconcile() {
        UUID order = uuid();
        long start = System.nanoTime();

        PaymentResponse res = paymentService.process(request(order, "100.00", "tok_timeout", "pay-" + order));

        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(900);   // timeout-simulation=PT1S
        assertThat(paymentService.findByOrder(order)).singleElement()
                .satisfies(p -> {
                    assertThat(p.paymentId()).isEqualTo(res.paymentId());
                    assertThat(p.status()).isEqualTo(PaymentStatus.APPROVED);
                });
    }

    // ---------- consultas ----------

    @Test
    void orderWithoutPaymentsReturnsEmptyList() {
        assertThat(paymentService.findByOrder(uuid())).isEmpty();
    }

    @Test
    void unknownPaymentIsNotFound() {
        assertThatThrownBy(() -> paymentService.get(uuid()))
                .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("NOT_FOUND");
    }

    // ---------- datos sensibles ----------

    @Test
    void logsNeverContainThePaymentMethodToken(CapturedOutput output) {
        String secretToken = "tok_SECRET_do_not_log_123456";

        paymentService.process(request(uuid(), "100.00", secretToken, "k-" + uuid()));

        assertThat(output.getAll()).doesNotContain(secretToken);
        assertThat(request(uuid(), "1.00", secretToken, "k").toString()).doesNotContain(secretToken);
    }
}
