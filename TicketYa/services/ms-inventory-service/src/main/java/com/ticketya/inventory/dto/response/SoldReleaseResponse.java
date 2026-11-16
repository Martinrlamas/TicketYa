package com.ticketya.inventory.dto.response;

import java.util.UUID;

public record SoldReleaseResponse(UUID releaseId, int released) {}
