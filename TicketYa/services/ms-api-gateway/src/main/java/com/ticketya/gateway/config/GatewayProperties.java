package com.ticketya.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        String catalogUrl, String inventoryUrl, String orderUrl, String ticketUrl,
        @DefaultValue Cors cors) {

    public record Cors(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {}
}
