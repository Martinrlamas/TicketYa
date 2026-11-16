package com.ticketya.platform.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.platform.correlation.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Una línea de log JSON por solicitud: método, ruta, userId, status, duración (ms) y correlationId.
 * Registra solo la ruta (sin query string ni body) para no filtrar datos sensibles.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String LOGGER_NAME = "ticketya.http";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private final ObjectMapper mapper;

    public RequestLoggingFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        int failureStatus = 0;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failureStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;     // el contenedor responderá 500
            throw e;
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            int status = failureStatus != 0 ? failureStatus : response.getStatus();
            emit(request, status, millis);
        }
    }

    private void emit(HttpServletRequest request, int status, long millis) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("event", "http_request");
        line.put("method", request.getMethod());
        line.put("path", request.getRequestURI());
        line.put("userId", sanitize(request.getHeader("X-User-Id")));
        line.put("status", status);
        line.put("durationMs", millis);
        line.put("correlationId", CorrelationId.current());
        try {
            log.info(mapper.writeValueAsString(line));
        } catch (IOException e) {
            log.warn("no se pudo serializar el log de la solicitud: {}", e.getMessage());
        }
    }

    /** El userId viene de un header del cliente: se limita para que no ensucie el log. */
    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 64 ? value.replaceAll("[\\r\\n]", "") : value.substring(0, 64);
    }
}
