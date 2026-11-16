package com.ticketya.payment.service;

import com.ticketya.payment.dto.request.CreatePaymentRequest;
import com.ticketya.platform.idempotency.Fingerprint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Huella de los datos que definen un pago: orden, cliente, monto, moneda y medio.
 * El monto se normaliza a 2 decimales (90000.0 == 90000.00) y token/last4 quedan afuera a propósito,
 * para que ningún dato del medio de pago intervenga en un cálculo auxiliar.
 */
@Component
public class RequestHasher {

    public String hash(CreatePaymentRequest r) {
        return Fingerprint.sha256(String.join("|",
                r.orderId().toString(),
                r.customerId().toString(),
                normalize(r.amount()).toPlainString(),
                r.currency(),
                r.method().name()));
    }

    static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
