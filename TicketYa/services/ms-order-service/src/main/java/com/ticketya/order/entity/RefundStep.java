package com.ticketya.order.entity;

/** Último paso completado de la saga de devolución. El orden de declaración es el orden de ejecución. */
public enum RefundStep {
    NONE,
    TICKET_INVALIDATED,
    SEAT_RELEASED,
    PAYMENT_REFUNDED;

    public boolean isBefore(RefundStep other) {
        return ordinal() < other.ordinal();
    }
}
