package com.ticketya.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticketya.catalog.client.InventoryClient;
import com.ticketya.catalog.client.OrderClient;

/** Humo: el contexto completo arranca con Flyway + validate y el starter conectado. Requiere Docker. */
@SpringBootTest(properties = "ticketya.platform.internal-token=test-token")
@ActiveProfiles("test")
@Testcontainers
class CatalogApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ApplicationContext context;

    @Test
    void contextLoadsWithStarterAndClients() {
        assertThat(context.getBean(InventoryClient.class)).isNotNull();
        assertThat(context.getBean(OrderClient.class)).isNotNull();
        assertThat(context.containsBean("globalExceptionHandler")).isTrue();
    }
}
