package com.ticketya.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.gateway.filter.CorrelationIdWebFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Convierte cualquier error del gateway al formato estándar (RFC 7807) con el mismo contrato que los servicios:
 * type, title, status, detail, code, correlationId. Orden -2: antes del handler por defecto de Spring Boot.
 */
@Component
@Order(-2)
public class ProblemErrorWebExceptionHandler implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ProblemErrorWebExceptionHandler.class);
    private final ObjectMapper mapper;

    public ProblemErrorWebExceptionHandler(ObjectMapper mapper) { this.mapper = mapper; }

    private record Problem(HttpStatus status, String code, String title, String detail) {}

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) return Mono.error(ex);
        Problem p = map(ex);
        if (p.status().is5xxServerError()) log.warn("gateway_error status={} code={} cause={}", p.status().value(), p.code(), ex.toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://ticketya.example/errors/" + p.code().toLowerCase().replace('_', '-'));
        body.put("title", p.title());
        body.put("status", p.status().value());
        body.put("detail", p.detail());
        body.put("code", p.code());
        body.put("correlationId", exchange.getAttributeOrDefault(CorrelationIdWebFilter.ATTR, ""));

        exchange.getResponse().setStatusCode(p.status());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        try {
            DataBuffer buf = exchange.getResponse().bufferFactory().wrap(mapper.writeValueAsBytes(body));
            return exchange.getResponse().writeWith(Mono.just(buf));
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    private Problem map(Throwable ex) {
        if (ex instanceof GatewayException g) return new Problem(g.status(), g.code(), g.title(), g.getMessage());
        if (ex instanceof ResponseStatusException r) {
            int s = r.getStatusCode().value();
            if (s == 404) return new Problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "No encontrado", "La ruta solicitada no existe");
            if (s >= 500) return upstream();
            return new Problem(HttpStatus.valueOf(s), "VALIDATION_ERROR", "Solicitud inválida", r.getReason() == null ? "Solicitud inválida" : r.getReason());
        }
        if (isUpstreamFailure(ex)) return upstream();
        return new Problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno", "Error interno");
    }

    private Problem upstream() {
        return new Problem(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", "Servicio no disponible",
                "El servicio solicitado no está disponible o no respondió a tiempo");
    }

    /** Conexión rechazada, DNS, reset, timeout de lectura/conexión (cualquiera en la cadena de causas). */
    private boolean isUpstreamFailure(Throwable ex) {
        for (Throwable c = ex; c != null; c = c.getCause()) {
            if (c instanceof IOException || c instanceof TimeoutException || c.getClass().getSimpleName().contains("Timeout")) return true;
            if (c.getClass().getName().endsWith("NotFoundException")) return true;     // gateway: sin destino
            if (c.getCause() == c) break;
        }
        return false;
    }
}
