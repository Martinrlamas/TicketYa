package com.ticketya.order.service;

import com.ticketya.order.exception.OrderException;

/** Valida el header Idempotency-Key de los endpoints que cobran o reservan. */
final class IdempotencyKey {

    private IdempotencyKey() {}

    /** Devuelve la clave sin espacios; 400 IDEMPOTENCY_KEY_REQUIRED si falta o está vacía. */
    static String require(String header) {
        if (header == null || header.isBlank()) {
            throw OrderException.claveIdempotenciaRequerida();
        }
        return header.trim();
    }
}
