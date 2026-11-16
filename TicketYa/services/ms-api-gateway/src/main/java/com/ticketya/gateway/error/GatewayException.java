package com.ticketya.gateway.error;

import org.springframework.http.HttpStatus;

/** Error generado por el propio gateway (identidad, ruta bloqueada). Se traduce a application/problem+json. */
public class GatewayException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String title;

    public GatewayException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status; this.code = code; this.title = title;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String title() { return title; }

    public static GatewayException unauthenticated(String detail) { return new GatewayException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No autenticado", detail); }
    public static GatewayException noEncontrado(String detail) { return new GatewayException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No encontrado", detail); }
}
