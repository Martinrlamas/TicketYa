package com.ticketya.platform.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketya.platform.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class CorrelationIdFilterTest {

    @Autowired MockMvc mvc;

    @Test
    void sin_header_genera_uno_lo_expone_al_controller_y_lo_devuelve() throws Exception {
        var result = mvc.perform(get("/api/public/ping")).andExpect(status().isOk()).andReturn();

        String header = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(header).isNotBlank();
        assertThat(java.util.UUID.fromString(header)).isNotNull();
        assertThat(result.getResponse().getContentAsString()).contains(header);     // el MDC lo tenía durante el request
    }

    @Test
    void propaga_el_del_cliente() throws Exception {
        mvc.perform(get("/api/public/ping").header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void reemplaza_un_valor_invalido_o_demasiado_largo() throws Exception {
        var result = mvc.perform(get("/api/public/ping").header("X-Correlation-Id", "x".repeat(200))).andReturn();
        assertThat(result.getResponse().getHeader("X-Correlation-Id")).hasSizeLessThan(100);

        var injected = mvc.perform(get("/api/public/ping").header("X-Correlation-Id", "a b;<script>")).andReturn();
        assertThat(injected.getResponse().getHeader("X-Correlation-Id")).doesNotContain(" ", ";", "<");
    }

    @Test
    void limpia_el_MDC_al_terminar() throws Exception {
        mvc.perform(get("/api/public/ping").header("X-Correlation-Id", "limpiar-1"));
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void tambien_sale_en_las_respuestas_de_error() throws Exception {
        mvc.perform(get("/api/secure/me").header("X-Correlation-Id", "err-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "err-1"))
                .andExpect(jsonPath("$.correlationId").value("err-1"));
    }
}
