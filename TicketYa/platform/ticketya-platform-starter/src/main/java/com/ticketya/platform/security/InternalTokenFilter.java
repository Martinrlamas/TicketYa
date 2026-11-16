package com.ticketya.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.platform.error.ErrorCode;
import com.ticketya.platform.error.ProblemFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protege {@code /internal/**}: exige X-Internal-Token con el secreto compartido (403 FORBIDDEN si falta o no coincide).
 * Falla cerrado: si no hay secreto configurado se rechazan todas las llamadas internas.
 * El gateway nunca rutea estas rutas; este filtro es la segunda barrera.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    public static final String PREFIX = "/internal/";
    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);

    private final byte[] expected;
    private final ObjectMapper mapper;

    public InternalTokenFilter(String token, ObjectMapper mapper) {
        this.expected = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
        if (expected.length == 0) {
            log.warn("ticketya.platform.internal-token está vacío: se rechazarán todas las llamadas a /internal/**");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return !path.startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        boolean valid = expected.length > 0 && provided != null
                && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8));    // comparación en tiempo constante
        if (!valid) {
            log.warn("acceso a {} rechazado: X-Internal-Token ausente o inválido", request.getRequestURI());
            ProblemDetail pd = ProblemFactory.of(ErrorCode.FORBIDDEN, "Falta o es inválido " + HEADER);
            response.setStatus(pd.getStatus());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            mapper.writeValue(response.getWriter(), pd);
            return;
        }
        chain.doFilter(request, response);
    }
}
