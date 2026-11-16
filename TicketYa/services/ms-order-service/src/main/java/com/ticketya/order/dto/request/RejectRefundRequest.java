package com.ticketya.order.dto.request;

import jakarta.validation.constraints.Size;

public record RejectRefundRequest(@Size(max = 500) String reason) {}
