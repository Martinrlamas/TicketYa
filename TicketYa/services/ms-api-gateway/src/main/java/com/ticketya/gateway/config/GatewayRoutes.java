package com.ticketya.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * Tabla de ruteo explícita. Se evalúa por {@code order} ascendente: la ruta más específica va primero,
 * así {@code GET /api/events/{id}/availability} nunca cae en catalog.
 * <ul>
 *   <li>/internal/** → NO se rutea (lo bloquea InternalPathBlockWebFilter, que además quita X-Internal-Token)</li>
 *   <li>payment-service → sin rutas públicas</li>
 * </ul>
 */
@Configuration
public class GatewayRoutes {

    @Bean
    RouteLocator ticketyRoutes(RouteLocatorBuilder b, GatewayProperties p) {
        return b.routes()
            .route("inventory-availability", r -> r.order(10)
                    .path("/api/events/*/availability").and().method(HttpMethod.GET)
                    .uri(p.inventoryUrl()))
            .route("catalog", r -> r.order(20)
                    .path("/api/venues/**", "/api/events/**")
                    .uri(p.catalogUrl()))
            .route("orders", r -> r.order(30)
                    .path("/api/orders/**", "/api/refund-requests/**")
                    .uri(p.orderUrl()))
            .route("tickets", r -> r.order(40)
                    .path("/api/tickets/**")
                    .uri(p.ticketUrl()))
            .build();
    }
}
