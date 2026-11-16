package com.ticketya.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Medio de pago. El token nunca se loguea ni se persiste en order-service. */
public record PayOrderRequest(
        @NotNull PaymentMethod method,
        @NotBlank @Size(max = 100) String token,
        @Pattern(regexp = "\\d{4}") String last4) {

    public enum PaymentMethod { CARD }

    @Override
    public String toString() {
        return "PayOrderRequest[method=" + method + "]";
    }
}
