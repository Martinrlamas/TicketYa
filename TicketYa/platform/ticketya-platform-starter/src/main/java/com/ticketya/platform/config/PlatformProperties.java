package com.ticketya.platform.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades {@code ticketya.platform.*}. El token interno toma por defecto la variable de entorno
 * {@code INTERNAL_TOKEN} (o {@code dev-internal-token} en desarrollo): ver {@link PlatformDefaultsPostProcessor}.
 */
@ConfigurationProperties(prefix = "ticketya.platform")
public record PlatformProperties(
        String internalToken,
        @DefaultValue Http http,
        @DefaultValue RequestLogging requestLogging,
        @DefaultValue Api api) {

    public record Http(
            @DefaultValue("PT1S") Duration connectTimeout,
            @DefaultValue("PT3S") Duration readTimeout) {}

    public record RequestLogging(@DefaultValue("true") boolean enabled) {}

    public record Api(@DefaultValue("1.0.0") String version) {}
}
