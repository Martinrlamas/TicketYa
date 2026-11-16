package com.ticketya.ticket;

import com.ticketya.ticket.dto.request.IssueTicketsRequest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** PostgreSQL real: la idempotencia concurrente depende de ON CONFLICT y de los UNIQUE de la base. */
@SpringBootTest
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

    @Autowired
    protected JdbcTemplate jdbc;

    protected static UUID uuid() {
        return UUID.randomUUID();
    }

    protected static IssueTicketsRequest.Item item(UUID orderItemId, UUID sectorId, String seatLabel) {
        return new IssueTicketsRequest.Item(orderItemId, sectorId, uuid(), seatLabel);
    }

    protected static IssueTicketsRequest issueRequest(UUID orderId, UUID eventId, UUID customerId,
                                                      IssueTicketsRequest.Item... items) {
        return new IssueTicketsRequest(orderId, eventId, customerId, List.copyOf(Arrays.asList(items)));
    }
}
