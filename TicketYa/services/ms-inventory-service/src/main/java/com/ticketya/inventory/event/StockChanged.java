package com.ticketya.inventory.event;

import java.util.UUID;

/** Evento interno: cambió el stock de un evento (hold, confirmación, liberación, expiración, devolución). */
public record StockChanged(UUID eventId) {}
