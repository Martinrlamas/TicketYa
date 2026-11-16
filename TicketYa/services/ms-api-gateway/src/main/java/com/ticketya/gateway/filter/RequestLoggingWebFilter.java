package com.ticketya.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Registra cada solicitud: método, ruta, destino, usuario, código de respuesta y tiempo. No registra cuerpos ni headers sensibles. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestLoggingWebFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger("http");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long t0 = System.nanoTime();
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/actuator")) return chain.filter(exchange);

        // beforeCommit: el código de respuesta ya está definido (también cuando lo fija el manejador de errores).
        exchange.getResponse().beforeCommit(() -> {
            var route = exchange.<org.springframework.cloud.gateway.route.Route>getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            var status = exchange.getResponse().getStatusCode();
            log.info("http_request method={} path={} route={} userId={} role={} status={} durationMs={} correlationId={}",
                    exchange.getRequest().getMethod().name(),
                    path,
                    route == null ? "-" : route.getId(),
                    exchange.getAttributeOrDefault(IdentityWebFilter.USER_ATTR, "anonymous"),
                    exchange.getAttributeOrDefault(IdentityWebFilter.ROLE_ATTR, "-"),
                    status == null ? 200 : status.value(),
                    (System.nanoTime() - t0) / 1_000_000,
                    exchange.getAttributeOrDefault(CorrelationIdWebFilter.ATTR, "-"));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
