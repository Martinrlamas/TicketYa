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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class IdentityAndRoleTest {

    private static final String USER = "2c3d4e5f-7081-4293-a4af-1a2b3c4d5e67";

    @Autowired MockMvc mvc;

    private static MockHttpServletRequestBuilder as(String path, String role) {
        return get(path).header("X-User-Id", USER).header("X-User-Role", role);
    }

    @Test
    void sin_X_User_Id_responde_401_UNAUTHENTICATED() throws Exception {
        mvc.perform(get("/api/secure/me").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void con_un_rol_fuera_del_conjunto_responde_401() throws Exception {
        mvc.perform(as("/api/secure/me", "SUPERUSER")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(as("/api/secure/me", "customer")).andExpect(status().isUnauthorized());      // distingue mayúsculas
    }

    @Test
    void con_X_User_Id_que_no_es_UUID_o_sin_rol_responde_401() throws Exception {
        mvc.perform(get("/api/secure/me").header("X-User-Id", "123").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/secure/me").header("X-User-Id", USER)).andExpect(status().isUnauthorized());
    }

    @Test
    void un_customer_en_un_endpoint_de_admin_recibe_403_FORBIDDEN() throws Exception {
        mvc.perform(as("/api/admin/only", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void el_admin_accede_y_se_valida_cada_rol_permitido() throws Exception {
        mvc.perform(as("/api/admin/only", "ADMIN")).andExpect(status().isOk());
        mvc.perform(as("/api/staff-or-admin", "ACCESS_STAFF")).andExpect(status().isOk());
        mvc.perform(as("/api/staff-or-admin", "ADMIN")).andExpect(status().isOk());
        mvc.perform(as("/api/staff-or-admin", "ORGANIZER")).andExpect(status().isForbidden());
    }

    @Test
    void sin_roles_en_la_anotacion_basta_con_estar_autenticado_y_la_identidad_se_inyecta() throws Exception {
        mvc.perform(as("/api/secure/me", "ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER))
                .andExpect(jsonPath("$.role").value("ORGANIZER"));
    }

    @Test
    void un_endpoint_sin_anotacion_es_publico() throws Exception {
        mvc.perform(get("/api/public/ping")).andExpect(status().isOk());
    }
}
