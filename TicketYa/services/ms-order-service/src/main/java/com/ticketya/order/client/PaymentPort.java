package com.ticketya.order.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Puerto hacia payment-service. Un rechazo NO es excepción: viene como {@code status=REJECTED}. */
public interface PaymentPort {

    PaymentInfo charge(ChargeCommand command);

    /** Reconciliación: lo que payment-service realmente registró para la orden. Lista vacía si no hay pagos. */
    List<PaymentInfo> findByOrder(UUID orderId);

    /** Idempotente por {@code idempotencyKey}. */
    void refund(UUID paymentId, BigDecimal amount, String reason, String idempotencyKey);

    record ChargeCommand(UUID orderId, UUID customerId, BigDecimal amount, String currency,
                         String method, String token, String last4, String idempotencyKey) {

        @Override
        public String toString() {      // nunca imprime el token
            return "ChargeCommand[orderId=" + orderId + ", amount=" + amount + "]";
        }
    }

    record PaymentInfo(UUID paymentId, UUID orderId, String status, String method, String last4) {

        public boolean isApproved() { return "APPROVED".equals(status); }
        public boolean isRejected() { return "REJECTED".equals(status); }
    }
}
