package com.ticketya.catalog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.ticketya.platform.http.PlatformRestClientFactory;

@Configuration
public class ClientsConfig {

    @Bean
    RestClient inventoryRestClient(PlatformRestClientFactory f, @Value("${ticketya.clients.inventory-url}") String url) {
        return f.createInternal(url);
    }

    @Bean
    RestClient orderRestClient(PlatformRestClientFactory f, @Value("${ticketya.clients.order-url}") String url) {
        return f.createInternal(url);
    }
}
