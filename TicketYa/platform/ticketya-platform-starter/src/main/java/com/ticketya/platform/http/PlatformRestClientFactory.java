package com.ticketya.platform.http;

import com.ticketya.platform.config.PlatformProperties;
import com.ticketya.platform.correlation.CorrelationId;
import com.ticketya.platform.error.ErrorCode;
import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.security.InternalTokenFilter;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Fábrica de RestClient para llamadas entre servicios. Todos los clientes:
 * <ul>
 *   <li>tienen timeouts de conexión (1 s) y de lectura (3 s) configurables;</li>
 *   <li>propagan el X-Correlation-Id de la solicitud en curso;</li>
 *   <li>traducen timeouts, caídas y respuestas 5xx a {@code UPSTREAM_UNAVAILABLE} (503);</li>
 *   <li>dejan pasar los 4xx para que el llamador los interprete (errores de negocio del otro servicio).</li>
 * </ul>
 */
public class PlatformRestClientFactory {

    private final PlatformProperties props;

    public PlatformRestClientFactory(PlatformProperties props) {
        this.props = props;
    }

    /** Cliente para llamar a otro servicio (sin token interno). */
    public RestClient create(String baseUrl) {
        return builder(baseUrl).build();
    }

    /** Cliente para llamar a endpoints {@code /internal/**}: agrega X-Internal-Token. */
    public RestClient createInternal(String baseUrl) {
        return builder(baseUrl).defaultHeader(InternalTokenFilter.HEADER, props.internalToken()).build();
    }

    public RestClient.Builder builder(String baseUrl) {
        return builder(baseUrl, props.http().connectTimeout(), props.http().readTimeout());
    }

    public RestClient.Builder builder(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                // el primero es el más externo: traduce los errores de E/S que lanzan los demás
                .requestInterceptor((request, body, execution) -> {
                    try {
                        return execution.execute(request, body);
                    } catch (IOException e) {
                        throw new PlatformException(ErrorCode.UPSTREAM_UNAVAILABLE,
                                "El servicio " + request.getURI().getAuthority() + " no responde", e);
                    }
                })
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = CorrelationId.current();
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationId.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new PlatformException(ErrorCode.UPSTREAM_UNAVAILABLE,
                            "El servicio " + request.getURI().getAuthority() + " respondió " + response.getStatusCode().value());
                });
    }
}
