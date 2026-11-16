package com.ticketya.payment.gateway;

import java.math.BigDecimal;

/** {@code idempotencyKey} se envía a la pasarela como referencia de la operación. */
public record GatewayChargeRequest(String idempotencyKey, BigDecimal amount, String currency, String token) {

    @Override
    public String toString() {
        return "GatewayChargeRequest[idempotencyKey=" + idempotencyKey + ", amount=" + amount + ", currency=" + currency + "]";
    }
}
