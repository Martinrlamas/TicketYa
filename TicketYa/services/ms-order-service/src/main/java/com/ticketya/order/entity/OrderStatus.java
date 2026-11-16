package com.ticketya.order.entity;

/** Máquina de estados de la orden. CANCELLED_BY_EVENT lo usa el change 06 (cancelación de evento). */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_REJECTED,
    EXPIRED,
    CANCELLED,
    FAILED_REFUNDED,
    CANCELLED_BY_EVENT
}
