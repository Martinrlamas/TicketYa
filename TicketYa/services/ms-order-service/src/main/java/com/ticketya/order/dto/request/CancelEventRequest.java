package com.ticketya.order.dto.request;

import jakarta.validation.constraints.Size;

public record CancelEventRequest(@Size(max = 500) String reason) {}
