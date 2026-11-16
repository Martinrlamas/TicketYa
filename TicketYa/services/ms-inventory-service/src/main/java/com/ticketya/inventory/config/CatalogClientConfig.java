package com.ticketya.inventory.config;

import com.ticketya.platform.http.PlatformRestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Cliente hacia catalog-service; timeouts, token interno y correlation-id los resuelve el starter. */
@Configuration
public class CatalogClientConfig {

    @Bean
    RestClient catalogRestClient(PlatformRestClientFactory factory, @Value("${ticketya.clients.catalog-url}") String url) {
        return factory.createInternal(url);
    }
}
