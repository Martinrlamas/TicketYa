package com.ticketya.gateway.filter;

import com.ticketya.gateway.error.GatewayException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * /internal/** nunca se rutea. Además, un cliente externo no puede inyectar X-Internal-Token (se elimina de todo request).
 * Se responde 404 (no 403) para no revelar que existen endpoints internos.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class InternalPathBlockWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.equals("/internal") || path.startsWith("/internal/"))
            return Mono.error(GatewayException.noEncontrado("La ruta solicitada no existe"));
        if (exchange.getRequest().getHeaders().containsKey("X-Internal-Token"))
            exchange = exchange.mutate().request(r -> r.headers(h -> h.remove("X-Internal-Token"))).build();
        return chain.filter(exchange);
    }
}
