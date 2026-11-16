package com.ticketya.ticket.dto.response;

import java.util.UUID;

public record EventInvalidationResponse(UUID eventId, int invalidated) {}
