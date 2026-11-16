package com.ticketya.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Sin secreto configurado el filtro rechaza todo, aunque el cliente mande un token vacío o cualquiera. */
class InternalTokenFailClosedTest {

    @Test
    void sin_secreto_configurado_se_rechaza_todo() throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter("", new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/ping");
        request.addHeader("X-Internal-Token", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
