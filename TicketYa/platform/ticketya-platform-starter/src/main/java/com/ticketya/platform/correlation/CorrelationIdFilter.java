package com.ticketya.platform.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Garantiza que toda solicitud tenga un X-Correlation-Id: usa el del cliente (si es válido) o genera uno,
 * lo deja en el MDC (aparece en cada línea de log) y lo devuelve en la respuesta.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(CorrelationId.HEADER);
        String correlationId = CorrelationId.isAcceptable(incoming) ? incoming : CorrelationId.generate();
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);      // antes de la cadena: también sale en errores
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
