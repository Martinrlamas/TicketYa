package com.ticketya.payment.gateway;

import com.ticketya.payment.exception.PaymentException;
import org.springframework.http.HttpStatus;

/** La pasarela no pudo procesar la operación. 503: no se persiste nada y el llamador puede reintentar con la misma clave. */
public class GatewayUnavailableException extends PaymentException {

    public GatewayUnavailableException(String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", "Pasarela no disponible", detail);
    }
}
