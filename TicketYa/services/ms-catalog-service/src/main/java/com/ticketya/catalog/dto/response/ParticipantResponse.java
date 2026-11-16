package com.ticketya.catalog.dto.response;

import com.ticketya.catalog.entity.ParticipantRole;

public record ParticipantResponse(String name, ParticipantRole role) {
}
