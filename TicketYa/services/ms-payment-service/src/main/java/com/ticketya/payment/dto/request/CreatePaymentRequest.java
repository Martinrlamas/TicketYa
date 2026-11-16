package com.ticketya.payment.dto.request;

import com.ticketya.payment.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull UUID orderId,
        @NotNull UUID customerId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull PaymentMethod method,
        @NotBlank @Size(max = 100) String token,
        @Pattern(regexp = "\\d{4}") String last4,
        @NotBlank @Size(max = 100) String idempotencyKey) {

    /** El token y last4 nunca deben terminar en un log por un toString accidental. */
    @Override
    public String toString() {
        return "CreatePaymentRequest[orderId=" + orderId + ", idempotencyKey=" + idempotencyKey + "]";
    }
}
