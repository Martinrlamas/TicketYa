package com.ticketya.platform.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketya.platform.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class OpenApiConfigTest {

    @Autowired MockMvc mvc;

    @Test
    void api_docs_publica_titulo_version_y_los_headers_de_identidad() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("demo-service"))
                .andExpect(jsonPath("$.info.version").value("9.9.9"))
                .andExpect(jsonPath("$.components.securitySchemes['X-User-Id'].in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes['X-User-Id'].name").value("X-User-Id"))
                .andExpect(jsonPath("$.components.securitySchemes['X-User-Role'].name").value("X-User-Role"))
                .andExpect(jsonPath("$.paths['/api/public/ping']").exists());
    }

    @Test
    void solo_los_endpoints_con_RequireRole_exigen_los_headers_de_identidad() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/secure/me'].get.security[0]['X-User-Id']").exists())
                .andExpect(jsonPath("$.paths['/api/secure/me'].get.security[0]['X-User-Role']").exists())
                .andExpect(jsonPath("$.paths['/api/public/ping'].get.security").doesNotExist());
    }
}
