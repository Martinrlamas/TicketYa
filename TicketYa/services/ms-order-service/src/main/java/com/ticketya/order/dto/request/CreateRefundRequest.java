package com.ticketya.order.dto.request;

import jakarta.validation.constraints.Size;

public record CreateRefundRequest(@Size(max = 500) String reason) {}
