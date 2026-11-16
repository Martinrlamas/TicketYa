package com.ticketya.platform.error;

import com.ticketya.platform.correlation.CorrelationId;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Arma respuestas RFC 7807 con los campos obligatorios: type, title, status, detail, code y correlationId. */
public final class ProblemFactory {

    public static final String TYPE_BASE = "https://ticketya.example/errors/";

    private ProblemFactory() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        return build(code.status(), code.name(), code.title(), detail);
    }

    public static ProblemDetail build(HttpStatus status, String code, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(pd, code, title);
        return pd;
    }

    /** Completa type, title, code y correlationId de un ProblemDetail ya existente. */
    public static void enrich(ProblemDetail pd, String code, String title) {
        pd.setType(URI.create(TYPE_BASE + code.toLowerCase().replace('_', '-')));
        pd.setTitle(title);
        pd.setProperty("code", code);
        pd.setProperty("correlationId", CorrelationId.current());
    }
}
