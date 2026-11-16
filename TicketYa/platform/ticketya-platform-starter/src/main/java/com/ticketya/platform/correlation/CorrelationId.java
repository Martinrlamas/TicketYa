package com.ticketya.platform.correlation;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** Acceso al identificador de correlación de la solicitud en curso (vive en el MDC). */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._\\-]{1,100}");

    private CorrelationId() {}

    /** El correlationId de la solicitud actual, o {@code null} fuera de una solicitud. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /** Un valor que viene del cliente solo se acepta si es corto y sin caracteres raros (evita inyección en logs). */
    public static boolean isAcceptable(String value) {
        return value != null && SAFE.matcher(value).matches();
    }
}
