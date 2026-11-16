package com.ticketya.catalog.dto.response;

import java.util.UUID;

import com.ticketya.catalog.entity.EventStatus;

public record InternalStatusResponse(UUID eventId, EventStatus status) {
}
