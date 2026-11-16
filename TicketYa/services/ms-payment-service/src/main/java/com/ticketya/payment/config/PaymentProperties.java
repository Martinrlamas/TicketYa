package com.ticketya.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        @DefaultValue("mock") String gateway,
        @DefaultValue Mock mock) {

    public record Mock(@DefaultValue("PT10S") Duration timeoutSimulation) {}
}
