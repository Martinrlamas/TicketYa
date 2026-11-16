package com.ticketya.order.config;

import com.ticketya.platform.http.PlatformRestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Un RestClient por servicio destino; timeouts, token interno y correlation-id los resuelve el starter. */
@Configuration
public class ClientsConfig {

    @Bean
    RestClient catalogRestClient(PlatformRestClientFactory f, @Value("${ticketya.clients.catalog-url}") String url) {
        return f.createInternal(url);
    }

    @Bean
    RestClient inventoryRestClient(PlatformRestClientFactory f, @Value("${ticketya.clients.inventory-url}") String url) {
        return f.createInternal(url);
    }

    @Bean
    RestClient paymentRestClient(PlatformRestClientFactory f, @Value("${ticketya.clients.payment-url}") String url) {
        return f.createInternal(url);
    }

    @Bean
    RestClient ticketRestClient(PlatformRestClientFactory f, @Value("${ticketya.clients.ticket-url}") String url) {
        return f.createInternal(url);
    }
}
