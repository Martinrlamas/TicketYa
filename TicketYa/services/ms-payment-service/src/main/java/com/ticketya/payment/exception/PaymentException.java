package com.ticketya.payment.exception;

import com.ticketya.platform.error.ApiException;
import com.ticketya.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error de negocio de payment-service; el GlobalExceptionHandler del starter lo traduce a RFC 7807. */
public class PaymentException extends ApiException {

    private PaymentException(ErrorCode code, String detail) {
        super(code, detail);
    }

    protected PaymentException(HttpStatus status, String code, String title, String detail) {
        super(status, code, title, detail);
    }

    public static PaymentException noEncontrado(String detail) {
        return new PaymentException(ErrorCode.NOT_FOUND, detail);
    }

    public static PaymentException claveIdempotenciaReutilizada() {
        return new PaymentException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "La clave ya se usó con otro monto u orden");
    }

    /** La spec pide 422 con code VALIDATION_ERROR para un reintegro que supera lo pagado. */
    public static PaymentException reintegroExcedido(String detail) {
        return new PaymentException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_ERROR.name(),
                "Reintegro excedido", detail);
    }

    public static PaymentException estadoInvalido(String detail) {
        return new PaymentException(ErrorCode.INVALID_STATE_TRANSITION, detail);
    }
}
