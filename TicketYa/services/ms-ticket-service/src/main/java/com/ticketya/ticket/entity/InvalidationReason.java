package com.ticketya.ticket.entity;

/** Motivo de invalidación; determina el estado destino. */
public enum InvalidationReason {
    REFUNDED(TicketStatus.REFUNDED),
    CANCELLED(TicketStatus.CANCELLED);

    private final TicketStatus target;

    InvalidationReason(TicketStatus target) {
        this.target = target;
    }

    public TicketStatus targetStatus() {
        return target;
    }
}
