package com.ticketya.order;

import com.ticketya.order.client.CatalogPort;
import com.ticketya.order.client.InventoryPort;
import com.ticketya.order.client.PaymentPort;
import com.ticketya.order.client.TicketPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL real (UPDATE condicional de la cuota, advisory locks, FOR UPDATE SKIP LOCKED) y los puertos
 * hacia los otros servicios reemplazados por mocks. El E2E real con el gateway es del change 07.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean protected CatalogPort catalog;
    @MockBean protected InventoryPort inventory;
    @MockBean protected PaymentPort payments;
    @MockBean protected TicketPort tickets;

    @Autowired protected JdbcTemplate jdbc;
}
