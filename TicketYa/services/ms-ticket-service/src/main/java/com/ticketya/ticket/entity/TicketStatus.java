package com.ticketya.ticket.entity;

/** Ciclo de vida: VALID -> USED (E2) | REFUNDED | CANCELLED. Los tres últimos son terminales. */
public enum TicketStatus {
    VALID,
    USED,
    REFUNDED,
    CANCELLED
}
