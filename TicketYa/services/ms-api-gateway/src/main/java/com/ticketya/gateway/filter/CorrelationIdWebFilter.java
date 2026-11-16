package com.ticketya.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/** Primer punto de entrada: toma o genera X-Correlation-Id, lo reenvía a los servicios y lo devuelve al cliente. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdWebFilter implements WebFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTR = "ticketya.correlationId";
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");   // evita inyección en logs

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String id = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        exchange.getAttributes().put(ATTR, id);
        exchange.getResponse().getHeaders().set(HEADER, id);
        ServerWebExchange out = exchange.mutate().request(r -> r.headers(h -> h.set(HEADER, id))).build();
        return chain.filter(out);
    }
}
