package com.ticketya.payment;

import com.ticketya.payment.gateway.PaymentGateway;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** PostgreSQL real: el lock advisory y los UPDATE condicionales no se pueden validar con H2. */
@SpringBootTest(properties = "payment.mock.timeout-simulation=PT1S")
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

    /** Espía sobre el MockPaymentGateway real: permite contar cuántas veces se "cobró". */
    @SpyBean
    protected PaymentGateway gateway;

    @Autowired
    protected JdbcTemplate jdbc;

    protected static UUID uuid() {
        return UUID.randomUUID();
    }

    protected static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
