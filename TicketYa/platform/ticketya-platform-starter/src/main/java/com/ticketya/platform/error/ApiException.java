package com.ticketya.platform.error;

import org.springframework.http.HttpStatus;

/**
 * Base de todas las excepciones de negocio de los servicios. Lleva el status HTTP y el {@code code}
 * del catálogo de errores; el {@link GlobalExceptionHandler} la traduce a RFC 7807, por lo que ningún
 * servicio necesita su propio {@code @RestControllerAdvice}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;

    protected ApiException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    protected ApiException(ErrorCode code, String detail) {
        this(code.status(), code.name(), code.title(), detail);
    }

    protected ApiException(ErrorCode code, String detail, Throwable cause) {
        super(detail, cause);
        this.status = code.status();
        this.code = code.name();
        this.title = code.title();
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getTitle() { return title; }

    public boolean hasCode(String other) { return code.equals(other); }

    public boolean hasCode(ErrorCode other) { return code.equals(other.name()); }
}
