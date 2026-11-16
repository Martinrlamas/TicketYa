package com.ticketya.catalog.dto.request;

import com.ticketya.catalog.entity.EventStatus;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de PUT /internal/events/{id}/status (desde inventory): solo SOLD_OUT o PUBLISHED. */
public record UpdateStatusRequest(@NotNull EventStatus status) {
}
