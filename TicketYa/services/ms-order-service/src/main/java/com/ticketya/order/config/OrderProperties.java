package com.ticketya.order.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Reglas y jobs de order-service ({@code order.*}). Las URLs de otros servicios están en {@code ticketya.clients.*}. */
@ConfigurationProperties(prefix = "order")
public record OrderProperties(
        @DefaultValue("6") int purchaseLimit,
        @DefaultValue("600") int holdTtlSeconds,
        @DefaultValue Retry retry,
        @DefaultValue Jobs jobs,
        @DefaultValue Refunds refunds,
        @DefaultValue Cancellation cancellation) {

    public record Retry(@DefaultValue("3") int maxAttempts, @DefaultValue("PT0.2S") Duration backoff) {}

    public record Jobs(@DefaultValue Expire expire, @DefaultValue Reconcile reconcile) {}

    public record Expire(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("30000") long rateMs,
            @DefaultValue("100") int batchSize) {}

    public record Reconcile(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("30000") long rateMs,
            @DefaultValue("PT30S") Duration minAge,
            @DefaultValue("PT2M") Duration abandonAfter) {}

    /** Devoluciones: ventana, reintentos con backoff exponencial y job de recuperación. */
    public record Refunds(
            @DefaultValue("72") int windowHours,
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("PT5S") Duration baseBackoff,
            @DefaultValue("PT5M") Duration maxBackoff,
            @DefaultValue("PT60S") Duration lease,
            @DefaultValue("true") boolean recoveryEnabled,
            @DefaultValue("5000") long recoveryRateMs,
            @DefaultValue("20") int batchSize) {}

    /** Cancelación de eventos: procesamiento por lotes y job que termina lo que quedó pendiente. */
    public record Cancellation(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("15000") long rateMs,
            @DefaultValue("100") int batchSize) {}
}
