package com.ticketya.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el gateway contra "servicios" falsos (JDK HttpServer) que devuelven los headers que recibieron.
 * Verifica ruteo, identidad, bloqueo de /internal, correlation-id y errores en formato problem+json.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    static final UUID USER = UUID.fromString("2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67");
    static final AtomicInteger HITS = new AtomicInteger();
    static final ConcurrentLinkedQueue<String> PATHS = new ConcurrentLinkedQueue<>();
    static final HttpServer CATALOG = fake("catalog"), INVENTORY = fake("inventory"), ORDER = fake("order"), TICKET = fake("ticket");
    static final int DEAD_PORT = freePort();                       // nadie escucha: simula servicio caído

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry r) {
        r.add("gateway.catalog-url", () -> "http://localhost:" + CATALOG.getAddress().getPort());
        r.add("gateway.inventory-url", () -> "http://localhost:" + INVENTORY.getAddress().getPort());
        r.add("gateway.order-url", () -> "http://localhost:" + DEAD_PORT);          // order "caído"
        r.add("gateway.ticket-url", () -> "http://localhost:" + TICKET.getAddress().getPort());
    }

    @Autowired WebTestClient web;

    @BeforeEach void reset() { HITS.set(0); PATHS.clear(); }
    @AfterAll static void stop() { CATALOG.stop(0); INVENTORY.stop(0); ORDER.stop(0); TICKET.stop(0); }

    // ---------------------------------------------------------------- ruteo
    @Test void public_search_goes_to_catalog_without_identity() {
        web.get().uri("/api/events?city=Tandil").exchange()
           .expectStatus().isOk()
           .expectHeader().exists("X-Correlation-Id")
           .expectBody().jsonPath("$.service").isEqualTo("catalog");
    }

    @Test void availability_goes_to_inventory_not_catalog() {
        web.get().uri("/api/events/" + UUID.randomUUID() + "/availability").exchange()
           .expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("inventory");
    }

    @Test void tickets_route_to_ticket_service() {
        web.get().uri("/api/tickets").header("X-User-Id", USER.toString()).header("X-User-Role", "CUSTOMER").exchange()
           .expectStatus().isOk().expectBody().jsonPath("$.service").isEqualTo("ticket");
    }

    @Test void payment_service_has_no_public_route() {
        web.get().uri("/api/payments/123").header("X-User-Id", USER.toString()).header("X-User-Role", "ADMIN").exchange()
           .expectStatus().isNotFound()
           .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
           .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(HITS.get()).isZero();
    }

    // ---------------------------------------------------------------- /internal
    @Test void internal_paths_are_blocked_and_never_reach_a_service() {
        web.post().uri("/internal/holds").header("X-Internal-Token", "anything").exchange()
           .expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(HITS.get()).isZero();
    }

    @Test void client_cannot_inject_internal_token_into_public_routes() {
        web.get().uri("/api/events").header("X-Internal-Token", "secret").exchange()
           .expectStatus().isOk().expectBody().jsonPath("$.internalToken").isEqualTo("");
    }

    // ---------------------------------------------------------------- identidad
    @Test void protected_route_without_identity_is_401_and_does_not_reach_service() {
        web.get().uri("/api/tickets").exchange()
           .expectStatus().isUnauthorized()
           .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
           .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED").jsonPath("$.correlationId").isNotEmpty();
        assertThat(HITS.get()).isZero();
    }

    @Test void invalid_role_or_user_id_is_401() {
        web.get().uri("/api/tickets").header("X-User-Id", USER.toString()).header("X-User-Role", "HACKER").exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/tickets").header("X-User-Id", "not-a-uuid").header("X-User-Role", "CUSTOMER").exchange().expectStatus().isUnauthorized();
        assertThat(HITS.get()).isZero();
    }

    @Test void my_events_is_not_public() {
        web.get().uri("/api/events/mine").exchange().expectStatus().isUnauthorized();
    }

    @Test void valid_identity_is_forwarded_to_the_service() {
        web.get().uri("/api/tickets").header("X-User-Id", USER.toString()).header("X-User-Role", "CUSTOMER").exchange()
           .expectBody().jsonPath("$.userId").isEqualTo(USER.toString()).jsonPath("$.role").isEqualTo("CUSTOMER");
    }

    @Test void invalid_identity_on_public_route_is_dropped_not_forwarded() {
        web.get().uri("/api/events").header("X-User-Id", USER.toString()).header("X-User-Role", "HACKER").exchange()
           .expectStatus().isOk().expectBody().jsonPath("$.userId").isEqualTo("").jsonPath("$.role").isEqualTo("");
    }

    @Test void cors_preflight_does_not_require_identity() {
        web.options().uri("/api/orders").header("Origin", "http://localhost:3000")
           .header("Access-Control-Request-Method", "POST").exchange().expectStatus().isOk();
    }

    // ---------------------------------------------------------------- correlation id
    @Test void incoming_correlation_id_is_preserved_and_forwarded() {
        web.get().uri("/api/events").header("X-Correlation-Id", "trace-12345678").exchange()
           .expectHeader().valueEquals("X-Correlation-Id", "trace-12345678")
           .expectBody().jsonPath("$.correlationId").isEqualTo("trace-12345678");
    }

    @Test void unsafe_correlation_id_is_replaced_by_a_generated_one() {
        var res = web.get().uri("/api/events").header("X-Correlation-Id", "bad value!").exchange().returnResult(String.class);
        String id = res.getResponseHeaders().getFirst("X-Correlation-Id");
        assertThat(id).isNotEqualTo("bad value!");
        assertThat(UUID.fromString(id)).isNotNull();
    }

    // ---------------------------------------------------------------- upstream caído
    @Test void unavailable_service_returns_503_problem_json() {
        web.get().uri("/api/orders").header("X-User-Id", USER.toString()).header("X-User-Role", "CUSTOMER").exchange()
           .expectStatus().isEqualTo(503)
           .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
           .expectBody().jsonPath("$.code").isEqualTo("UPSTREAM_UNAVAILABLE");
    }

    @Test void unknown_route_with_identity_is_404_problem_json() {
        web.get().uri("/api/nope").header("X-User-Id", USER.toString()).header("X-User-Role", "CUSTOMER").exchange()
           .expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test void public_contract_is_served_statically() {
        web.get().uri("/openapi/ticketya-public-api.yaml").exchange().expectStatus().isOk();
    }

    // ---------------------------------------------------------------- servicios falsos
    private static HttpServer fake(String name) {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            s.createContext("/", ex -> {
                HITS.incrementAndGet();
                PATHS.add(ex.getRequestURI().getPath());
                HttpHeaders h = new HttpHeaders();
                ex.getRequestHeaders().forEach((k, v) -> h.put(k, v));
                String json = "{\"service\":\"" + name + "\",\"path\":\"" + ex.getRequestURI().getPath() + "\""
                        + ",\"userId\":\"" + nz(h.getFirst("X-User-Id")) + "\",\"role\":\"" + nz(h.getFirst("X-User-Role")) + "\""
                        + ",\"correlationId\":\"" + nz(h.getFirst("X-Correlation-Id")) + "\""
                        + ",\"internalToken\":\"" + nz(h.getFirst("X-Internal-Token")) + "\"}";
                byte[] b = json.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            s.start();
            return s;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private static String nz(String s) { return s == null ? "" : s; }
    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
