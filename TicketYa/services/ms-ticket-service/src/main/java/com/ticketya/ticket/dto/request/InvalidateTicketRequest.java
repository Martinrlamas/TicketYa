package com.ticketya.ticket.dto.request;

import com.ticketya.ticket.entity.InvalidationReason;
import jakarta.validation.constraints.NotNull;

/** Sirve tanto para una entrada ({reason: REFUNDED|CANCELLED}) como para la invalidación masiva ({reason: CANCELLED}). */
public record InvalidateTicketRequest(@NotNull InvalidationReason reason) {}
