package com.ticketya.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.payment.AbstractPostgresTest;
import com.ticketya.payment.dto.request.CreatePaymentRequest;
import com.ticketya.payment.dto.request.CreateRefundRequest;
import com.ticketya.payment.entity.PaymentMethod;
import com.ticketya.payment.entity.RefundStatus;
import com.ticketya.payment.exception.PaymentException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RefundServiceTest extends AbstractPostgresTest {

    @Autowired PaymentService paymentService;
    @Autowired RefundService refundService;

    private UUID pay(String amount, String token) {
        UUID order = uuid();
        return paymentService.process(new CreatePaymentRequest(order, uuid(), money(amount), "ARS",
                PaymentMethod.CARD, token, "4242", "pay-" + order)).paymentId();
    }

    private static CreateRefundRequest refundOf(String amount, String key) {
        return new CreateRefundRequest(money(amount), "REFUND_REQUEST", key);
    }

    private BigDecimal refunded(UUID paymentId) {
        return jdbc.queryForObject("select refunded_amount from payment where id = ?", BigDecimal.class, paymentId);
    }

    @Test
    void totalRefundEqualsPaidAmount() {
        UUID paymentId = pay("90000.00", "tok_approved");

        var result = refundService.refund(paymentId, refundOf("90000.00", "refund-" + uuid()));

        assertThat(result.created()).isTrue();
        assertThat(result.body().status()).isEqualTo(RefundStatus.APPROVED);
        assertThat(refunded(paymentId)).isEqualByComparingTo("90000.00");
    }

    @Test
    void repeatedKeyReturnsOriginalRefundWithoutRefundingTwice() {
        UUID paymentId = pay("1000.00", "tok_approved");
        String key = "refund-" + uuid();

        var first = refundService.refund(paymentId, refundOf("400.00", key));
        var again = refundService.refund(paymentId, refundOf("400.00", key));

        assertThat(again.created()).isFalse();                       // => 200
        assertThat(again.body().refundId()).isEqualTo(first.body().refundId());
        assertThat(refunded(paymentId)).isEqualByComparingTo("400.00");
    }

    @Test
    void refundOverRemainingBalanceIsRejectedWith422() {
        UUID paymentId = pay("1000.00", "tok_approved");
        refundService.refund(paymentId, refundOf("700.00", "refund-" + uuid()));

        assertThatThrownBy(() -> refundService.refund(paymentId, refundOf("400.00", "refund-" + uuid())))
                .isInstanceOf(PaymentException.class)
                .satisfies(e -> {
                    assertThat(((PaymentException) e).getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(((PaymentException) e).getStatus().value()).isEqualTo(422);
                });
        assertThat(refunded(paymentId)).isEqualByComparingTo("700.00");
    }

    @Test
    void refundOfRejectedPaymentIsInvalidStateTransition() {
        UUID paymentId = pay("1000.00", "tok_rejected");

        assertThatThrownBy(() -> refundService.refund(paymentId, refundOf("100.00", "refund-" + uuid())))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void refundOfUnknownPaymentIsNotFound() {
        assertThatThrownBy(() -> refundService.refund(uuid(), refundOf("100.00", "refund-" + uuid())))
                .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("NOT_FOUND");
    }

    @Test
    void refundKeyReusedWithDifferentAmountIsRejected() {
        UUID paymentId = pay("1000.00", "tok_approved");
        String key = "refund-" + uuid();
        refundService.refund(paymentId, refundOf("100.00", key));

        assertThatThrownBy(() -> refundService.refund(paymentId, refundOf("200.00", key)))
                .isInstanceOf(PaymentException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    // ---------- 2.4 reintegro parcial acumulado bajo concurrencia ----------

    @Test
    void concurrentPartialRefundsNeverExceedThePaidAmount() throws Exception {
        UUID paymentId = pay("100.00", "tok_approved");

        // 10 reintegros de 30 con claves distintas: solo caben 3 (90); el resto excede el saldo
        Map<String, Integer> result = ConcurrencyHelper.race(10,
                i -> refundService.refund(paymentId, refundOf("30.00", "refund-" + paymentId + "-" + i)));

        assertThat(result).containsEntry("OK", 3).containsEntry("VALIDATION_ERROR", 7);
        assertThat(refunded(paymentId)).isEqualByComparingTo("90.00");
        assertThat(jdbc.queryForObject("select count(*) from refund where payment_id = ?", Integer.class, paymentId))
                .isEqualTo(3);
    }

    @Test
    void concurrentRetriesOfSameRefundKeyRefundOnce() throws Exception {
        UUID paymentId = pay("100.00", "tok_approved");
        String key = "refund-" + uuid();

        Map<String, Integer> result = ConcurrencyHelper.race(10, i -> refundService.refund(paymentId, refundOf("50.00", key)));

        assertThat(result).containsOnlyKeys("OK").containsEntry("OK", 10);
        assertThat(refunded(paymentId)).isEqualByComparingTo("50.00");
    }
}
