package com.ticketya.inventory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "inventory")
public record InventoryProperties(
        @DefaultValue("PT10M") Duration holdTtl,
        @DefaultValue ExpireJob expireJob) {

    public record ExpireJob(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("15000") long rateMs,
            @DefaultValue("100") int batchSize) {}
}
