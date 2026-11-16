package com.ticketya.gateway.filter;

import com.ticketya.gateway.error.GatewayException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

/**
 * Identidad en el borde (Entrega 1: headers X-User-Id / X-User-Role, SIN autenticación real).
 * - Rutas protegidas: exige ambos headers válidos, si no 401.
 * - Rutas públicas: si vienen válidos se reenvían; si vienen inválidos se descartan (el servicio ve una solicitud anónima).
 * Los servicios vuelven a validar el rol (defensa en profundidad).
 *
 * Entrega 2: este es el ÚNICO lugar que cambia: valida el JWT y genera estos mismos headers (descartando los del cliente).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class IdentityWebFilter implements WebFilter {
    public static final String USER_ATTR = "ticketya.userId";
    public static final String ROLE_ATTR = "ticketya.role";
    static final String USER_ID = "X-User-Id", USER_ROLE = "X-User-Role";
    private static final Set<String> ROLES = Set.of("CUSTOMER", "ORGANIZER", "ADMIN", "ACCESS_STAFF");

    private static final PathPatternParser P = new PathPatternParser();
    private static final PathPattern SEARCH = P.parse("/api/events");
    private static final PathPattern EVENT_DETAIL = P.parse("/api/events/{id}");
    private static final PathPattern AVAILABILITY = P.parse("/api/events/{id}/availability");
    private static final PathPattern VENUE_DETAIL = P.parse("/api/venues/{id}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith("/api/")) return chain.filter(exchange);                 // actuator, swagger, contrato estático

        var headers = exchange.getRequest().getHeaders();
        String id = headers.getFirst(USER_ID), role = headers.getFirst(USER_ROLE);
        boolean valid = isValid(id, role);
        boolean isPublic = isPublic(exchange.getRequest().getMethod(), path);

        if (valid) {
            exchange.getAttributes().put(USER_ATTR, id);
            exchange.getAttributes().put(ROLE_ATTR, role);
            return chain.filter(exchange);
        }
        if (isPublic) {
            if (id == null && role == null) return chain.filter(exchange);
            return chain.filter(exchange.mutate().request(r -> r.headers(h -> { h.remove(USER_ID); h.remove(USER_ROLE); })).build());
        }
        return Mono.error(GatewayException.unauthenticated("Falta o es inválida la identidad (X-User-Id / X-User-Role)"));
    }

    static boolean isValid(String id, String role) {
        if (id == null || role == null || !ROLES.contains(role)) return false;
        try { UUID.fromString(id); return true; } catch (IllegalArgumentException e) { return false; }
    }

    /** Solo lectura de catálogo y disponibilidad (y preflight CORS). "/api/events/mine" es del organizador: NO es público. */
    static boolean isPublic(HttpMethod method, String path) {
        if (HttpMethod.OPTIONS.equals(method)) return true;
        if (!HttpMethod.GET.equals(method)) return false;
        PathContainer c = PathContainer.parsePath(path);
        if (SEARCH.matches(c) || AVAILABILITY.matches(c) || VENUE_DETAIL.matches(c)) return true;
        return EVENT_DETAIL.matches(c) && !path.equals("/api/events/mine");
    }
}
