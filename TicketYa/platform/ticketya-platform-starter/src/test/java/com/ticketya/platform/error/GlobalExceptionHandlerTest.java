package com.ticketya.platform.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketya.platform.testapp.TestApplication;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mvc;

    @Test
    void campo_obligatorio_vacio_es_400_VALIDATION_ERROR_con_el_detalle_del_campo() throws Exception {
        mvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"quantity\":0}")
                        .header("X-Correlation-Id", "val-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.correlationId").value("val-1"))
                .andExpect(jsonPath("$.detail", Matchers.containsString("name")))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'quantity')]").exists());
    }

    @Test
    void json_ilegible_es_400_VALIDATION_ERROR() throws Exception {
        mvc.perform(post("/api/validate").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void parametro_invalido_o_faltante_es_400_VALIDATION_ERROR() throws Exception {
        mvc.perform(get("/api/param").param("limit", "0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/param")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/param").param("limit", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void una_PlatformException_usa_el_status_y_el_codigo_del_catalogo() throws Exception {
        mvc.perform(get("/api/domain-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                .andExpect(jsonPath("$.title").value("Asiento no disponible"))
                .andExpect(jsonPath("$.detail").value("El asiento A-5 ya no está disponible"))
                .andExpect(jsonPath("$.type").value("https://ticketya.example/errors/seat-unavailable"));
    }

    @Test
    void un_error_inesperado_es_500_sin_filtrar_el_mensaje_interno() throws Exception {
        mvc.perform(get("/api/boom").header("X-Correlation-Id", "boom-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("boom-1"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("detalle-secreto-interno"))));
    }

    @Test
    void ruta_inexistente_es_404_NOT_FOUND_y_metodo_no_soportado_es_405() throws Exception {
        mvc.perform(get("/api/no-existe")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").exists());
        mvc.perform(delete("/api/public/ping")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void el_catalogo_tiene_los_15_codigos_con_los_status_de_la_spec() {
        org.assertj.core.api.Assertions.assertThat(ErrorCode.values()).hasSize(15);
        org.assertj.core.api.Assertions.assertThat(ErrorCode.PAYMENT_REJECTED.status().value()).isEqualTo(402);
        org.assertj.core.api.Assertions.assertThat(ErrorCode.HOLD_EXPIRED.status().value()).isEqualTo(410);
        org.assertj.core.api.Assertions.assertThat(ErrorCode.REFUND_WINDOW_CLOSED.status().value()).isEqualTo(422);
        org.assertj.core.api.Assertions.assertThat(ErrorCode.UPSTREAM_UNAVAILABLE.status().value()).isEqualTo(503);
        org.assertj.core.api.Assertions.assertThat(ErrorCode.EVENT_NOT_PURCHASABLE.status().value()).isEqualTo(409);
    }
}
