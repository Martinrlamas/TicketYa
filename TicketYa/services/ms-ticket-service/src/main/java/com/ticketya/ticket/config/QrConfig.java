package com.ticketya.ticket.config;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QrConfig {

    /** SecureRandom es thread-safe; una única instancia compartida. */
    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
