package com.ticketya.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.platform.testapp.TestApplication;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private static final String USER = "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode requestLine(CapturedOutput output, String path) throws Exception {
        for (String line : output.getOut().split("\\R")) {
            int brace = line.indexOf("{\"event\":\"http_request\"");
            if (brace >= 0) {
                JsonNode node = mapper.readTree(line.substring(brace));
                if (path.equals(node.get("path").asText())) {
                    return node;
                }
            }
        }
        throw new AssertionError("no hay línea de log para " + path + " en:\n" + output.getOut());
    }

    @Test
    void emite_una_linea_json_con_metodo_ruta_usuario_status_duracion_y_correlationId(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/secure/me").header("X-User-Id", USER).header("X-User-Role", "CUSTOMER")
                .header("X-Correlation-Id", "log-1"));

        JsonNode line = requestLine(output, "/api/secure/me");
        assertThat(line.get("method").asText()).isEqualTo("GET");
        assertThat(line.get("userId").asText()).isEqualTo(USER);
        assertThat(line.get("status").asInt()).isEqualTo(200);
        assertThat(line.get("durationMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(line.get("correlationId").asText()).isEqualTo("log-1");
    }

    @Test
    void registra_el_status_de_los_errores_y_no_incluye_query_string(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/param").param("limit", "0").param("token", "no-debe-aparecer"));

        JsonNode line = requestLine(output, "/api/param");
        assertThat(line.get("status").asInt()).isEqualTo(400);
        assertThat(output.getOut()).doesNotContain("no-debe-aparecer");
    }

    @Test
    void el_correlationId_aparece_en_las_lineas_de_log_de_la_aplicacion(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/boom").header("X-Correlation-Id", "log-boom"));

        // la línea "error no controlado" lleva el correlationId delante, por el patrón de log de la plataforma
        boolean found = Arrays.stream(output.getOut().split("\\R"))
                .anyMatch(l -> l.contains("error no controlado") && l.contains("[log-boom]"));
        assertThat(found).isTrue();
    }
}
