package com.ticketya.platform.error;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/** Catálogo de códigos de error de la plataforma (spec platform-conventions). */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request inválido"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Falta Idempotency-Key"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "No autenticado"),
    PAYMENT_REJECTED(HttpStatus.PAYMENT_REQUIRED, "Pago rechazado"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Acceso denegado"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso inexistente"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Estado inválido"),
    EVENT_NOT_PURCHASABLE(HttpStatus.CONFLICT, "Evento no disponible para la compra"),
    SEAT_UNAVAILABLE(HttpStatus.CONFLICT, "Asiento no disponible"),
    INSUFFICIENT_CAPACITY(HttpStatus.CONFLICT, "Capacidad insuficiente"),
    HOLD_EXPIRED(HttpStatus.GONE, "Reserva vencida"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY, "Clave reutilizada"),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Límite de compra superado"),
    REFUND_WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "Plazo de devolución vencido"),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() { return status; }
    public String title() { return title; }

    /** Busca un código por su nombre (p. ej. el {@code code} de un error devuelto por otro servicio). */
    public static Optional<ErrorCode> find(String name) {
        return Arrays.stream(values()).filter(c -> c.name().equals(name)).findFirst();
    }
}
