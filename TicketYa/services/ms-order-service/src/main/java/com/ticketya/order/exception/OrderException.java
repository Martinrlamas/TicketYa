package com.ticketya.order.exception;

import com.ticketya.platform.error.ApiException;
import com.ticketya.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Error de negocio de order-service; el GlobalExceptionHandler del starter lo traduce a RFC 7807.
 * También representa los errores de negocio de otros servicios (pass-through de status y code).
 */
public class OrderException extends ApiException {

    private OrderException(ErrorCode code, String detail) {
        super(code, detail);
    }

    private OrderException(HttpStatus status, String code, String title, String detail) {
        super(status, code, title, detail);
    }

    public static OrderException validacion(String detail) {
        return new OrderException(ErrorCode.VALIDATION_ERROR, detail);
    }

    public static OrderException claveIdempotenciaRequerida() {
        return new OrderException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "El header Idempotency-Key es obligatorio");
    }

    public static OrderException pagoRechazado() {
        return new OrderException(ErrorCode.PAYMENT_REJECTED, "La pasarela rechazó el pago");
    }

    public static OrderException noEncontrado(String detail) {
        return new OrderException(ErrorCode.NOT_FOUND, detail);
    }

    public static OrderException estadoInvalido(String detail) {
        return new OrderException(ErrorCode.INVALID_STATE_TRANSITION, detail);
    }

    public static OrderException eventoNoComprable(String detail) {
        return new OrderException(ErrorCode.EVENT_NOT_PURCHASABLE, detail);
    }

    public static OrderException reservaVencida(String detail) {
        return new OrderException(ErrorCode.HOLD_EXPIRED, detail);
    }

    public static OrderException ventanaDevolucionCerrada(int hours) {
        return new OrderException(ErrorCode.REFUND_WINDOW_CLOSED,
                "Las devoluciones se aceptan hasta " + hours + " horas antes del inicio del evento");
    }

    public static OrderException claveIdempotenciaReutilizada() {
        return new OrderException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "La clave ya se usó con otro contenido");
    }

    public static OrderException limiteCompraExcedido(int limit) {
        return new OrderException(ErrorCode.PURCHASE_LIMIT_EXCEEDED, "El máximo es de " + limit + " entradas por evento");
    }

    public static OrderException servicioNoDisponible(String detail) {
        return new OrderException(ErrorCode.UPSTREAM_UNAVAILABLE, detail);
    }

    /** Error de negocio devuelto por otro servicio (SEAT_UNAVAILABLE, HOLD_EXPIRED, ...): se conserva status y code. */
    public static OrderException desdeServicioExterno(HttpStatus status, String code, String detail) {
        String title = ErrorCode.find(code).map(ErrorCode::title).orElse("Error en servicio dependiente");
        return new OrderException(status, code, title, detail);
    }
}
