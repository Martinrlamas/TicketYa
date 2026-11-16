package com.ticketya.platform.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.ticketya.platform.config.PlatformProperties;
import com.ticketya.platform.error.ErrorCode;
import com.ticketya.platform.error.PlatformException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Servidor HTTP real en un puerto libre: se prueban timeouts, caídas, 5xx y la propagación de headers. */
class PlatformRestClientFactoryTest {

    private HttpServer server;
    private final AtomicReference<String> receivedCorrelation = new AtomicReference<>();
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private PlatformRestClientFactory factory;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            receivedCorrelation.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            reply(exchange, 200, "ok");
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, "tarde");
        });
        server.createContext("/boom", exchange -> reply(exchange, 500, "{}"));
        server.createContext("/unavailable", exchange -> reply(exchange, 503, "{}"));
        server.createContext("/business", exchange -> reply(exchange, 409, "{\"code\":\"SEAT_UNAVAILABLE\"}"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        factory = new PlatformRestClientFactory(new PlatformProperties("tok-interno",
                new PlatformProperties.Http(Duration.ofMillis(300), Duration.ofMillis(400)),
                new PlatformProperties.RequestLogging(true), new PlatformProperties.Api("1.0.0")));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        MDC.clear();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void propaga_el_correlationId_de_la_solicitud_en_curso() {
        MDC.put("correlationId", "corr-777");
        String body = factory.create(baseUrl).get().uri("/echo").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        assertThat(receivedCorrelation.get()).isEqualTo("corr-777");
        assertThat(receivedToken.get()).isNull();                       // el cliente común no manda el token interno
    }

    @Test
    void el_cliente_interno_agrega_X_Internal_Token() {
        factory.createInternal(baseUrl).get().uri("/echo").retrieve().body(String.class);
        assertThat(receivedToken.get()).isEqualTo("tok-interno");
    }

    @Test
    void un_timeout_de_lectura_se_traduce_a_UPSTREAM_UNAVAILABLE() {
        RestClient client = factory.create(baseUrl);
        assertThatThrownBy(() -> client.get().uri("/slow").retrieve().body(String.class))
                .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
    }

    @Test
    void un_servicio_caido_se_traduce_a_UPSTREAM_UNAVAILABLE() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        RestClient client = factory.create("http://127.0.0.1:" + closedPort);
        assertThatThrownBy(() -> client.get().uri("/echo").retrieve().body(String.class))
                .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
    }

    @Test
    void las_respuestas_5xx_se_traducen_a_UPSTREAM_UNAVAILABLE() {
        RestClient client = factory.create(baseUrl);
        for (String path : new String[] {"/boom", "/unavailable"}) {
            assertThatThrownBy(() -> client.get().uri(path).retrieve().body(String.class))
                    .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
        }
    }

    @Test
    void los_4xx_de_negocio_pasan_al_llamador_para_que_los_interprete() {
        RestClient client = factory.create(baseUrl);
        assertThatThrownBy(() -> client.get().uri("/business").retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(409);
                    assertThat(e.getResponseBodyAsString()).contains("SEAT_UNAVAILABLE");
                });
    }
}
