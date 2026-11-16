package com.ticketya.inventory;

import com.ticketya.inventory.client.EventStatusNotifier;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** PostgreSQL real (Testcontainers): la garantía anti doble venta depende del motor, H2 no sirve. */
@SpringBootTest(properties = "inventory.expire-job.enabled=false")
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

    @MockBean
    protected EventStatusNotifier notifier;

    @Autowired
    protected JdbcTemplate jdbc;

    protected static UUID uuid() {
        return UUID.randomUUID();
    }
}
