package com.ticketya.platform.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lock advisory de Postgres por clave, a nivel de transacción. Serializa a quienes usan la misma
 * Idempotency-Key ANTES de ejecutar efectos externos (cobrar, retener asientos): el UNIQUE por sí solo
 * detectaría el duplicado recién después. Se libera solo al terminar la transacción (commit o rollback).
 */
public class IdempotencyLock {

    private final JdbcTemplate jdbc;

    public IdempotencyLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String scope, String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, scope + ":" + key);
    }
}
