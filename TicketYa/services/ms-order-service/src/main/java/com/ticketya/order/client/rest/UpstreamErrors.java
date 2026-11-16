package com.ticketya.order.client.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.order.exception.OrderException;
import com.ticketya.platform.error.PlatformException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Traduce los errores HTTP de los servicios dependientes:
 * <ul>
 *   <li>4xx con {@code code} de negocio (SEAT_UNAVAILABLE, HOLD_EXPIRED, ...) => se conservan status y code;</li>
 *   <li>timeouts, caídas, 5xx (el RestClient del starter los lanza como PlatformException) y 401/403
 *       (mala configuración de token) => 503 UPSTREAM_UNAVAILABLE.</li>
 * </ul>
 */
final class UpstreamErrors {

    private static final Logger log = LoggerFactory.getLogger(UpstreamErrors.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Problem(String code, String detail) {}

    private UpstreamErrors() {}

    static <T> T call(String service, Supplier<T> action) {
        try {
            return action.get();
        } catch (PlatformException ex) {                    // 5xx o caída detectada por el cliente del starter
            log.warn("{} no disponible: {}", service, ex.getMessage());
            throw OrderException.servicioNoDisponible(ex.getMessage());
        } catch (RestClientResponseException ex) {
            throw translate(service, ex);
        } catch (ResourceAccessException ex) {
            log.warn("{} no responde: {}", service, ex.getMessage());
            throw OrderException.servicioNoDisponible(service + " no responde");
        } catch (RestClientException ex) {
            log.warn("{} falló: {}", service, ex.getMessage());
            throw OrderException.servicioNoDisponible(service + " falló");
        }
    }

    static void run(String service, Runnable action) {
        call(service, () -> {
            action.run();
            return null;
        });
    }

    private static OrderException translate(String service, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        boolean businessError = status >= 400 && status < 500 && status != 401 && status != 403;
        if (businessError) {
            Problem problem = parse(ex);
            if (problem != null && problem.code() != null) {
                return OrderException.desdeServicioExterno(HttpStatus.valueOf(status), problem.code(),
                        problem.detail() != null ? problem.detail() : service + " respondió " + status);
            }
        }
        log.warn("{} respondió {}", service, status);
        return OrderException.servicioNoDisponible(service + " respondió " + status);
    }

    private static Problem parse(RestClientResponseException ex) {
        try {
            return MAPPER.readValue(ex.getResponseBodyAsByteArray(), Problem.class);
        } catch (Exception e) {
            return null;
        }
    }
}
