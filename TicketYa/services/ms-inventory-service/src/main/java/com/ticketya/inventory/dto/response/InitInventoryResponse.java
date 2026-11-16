package com.ticketya.inventory.dto.response;

import java.util.UUID;

public record InitInventoryResponse(UUID eventId, int seatsCreated, int zonesCreated) {}
