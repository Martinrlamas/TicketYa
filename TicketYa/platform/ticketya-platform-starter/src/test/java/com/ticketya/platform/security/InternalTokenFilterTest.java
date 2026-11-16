package com.ticketya.platform.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketya.platform.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class InternalTokenFilterTest {

    @Autowired MockMvc mvc;

    @Test
    void sin_token_responde_403_FORBIDDEN_problem_json_con_correlationId() throws Exception {
        mvc.perform(get("/internal/ping").header("X-Correlation-Id", "int-1"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.correlationId").value("int-1"));
    }

    @Test
    void con_un_token_incorrecto_responde_403() throws Exception {
        mvc.perform(get("/internal/ping").header("X-Internal-Token", "otro")).andExpect(status().isForbidden());
        mvc.perform(get("/internal/ping").header("X-Internal-Token", "")).andExpect(status().isForbidden());
    }

    @Test
    void con_el_token_correcto_pasa() throws Exception {
        mvc.perform(get("/internal/ping").header("X-Internal-Token", "secret-for-tests")).andExpect(status().isOk());
    }

    @Test
    void no_afecta_a_las_rutas_publicas() throws Exception {
        mvc.perform(get("/api/public/ping")).andExpect(status().isOk());
    }

    @Test
    void una_ruta_que_solo_se_parece_a_internal_no_se_filtra() throws Exception {
        mvc.perform(get("/internals/ping")).andExpect(status().isNotFound());       // 404 normal, no 403
    }
}
