package com.ticketya.inventory.dto.response;

import com.ticketya.inventory.entity.HoldStatus;
import java.util.UUID;

public record ConfirmHoldResponse(UUID holdId, HoldStatus status) {}
