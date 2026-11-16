package com.ticketya.catalog.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class AppConfig {

    /** La zona del reloj es la que usan dateFrom/dateTo de la búsqueda. */
    @Bean
    Clock clock(@Value("${ticketya.catalog.zone:America/Argentina/Buenos_Aires}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    /** ETag en GETs (preparado para cache en E2). Las respuestas no incluyen disponibilidad. */
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        var r = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        r.addUrlPatterns("/api/venues/*", "/api/events", "/api/events/*");
        r.setName("etagFilter");
        return r;
    }

    @Bean
    OpenAPI catalogOpenApi() {
        return new OpenAPI().info(new Info().title("catalog-service").version("1.0.0")
                .description("Lugares, sectores, eventos, precios, ciclo de vida y búsqueda. "
                        + "Errores en application/problem+json (RFC 7807)."));
    }
}
