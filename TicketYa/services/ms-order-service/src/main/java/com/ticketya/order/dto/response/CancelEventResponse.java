package com.ticketya.order.dto.response;

import java.util.UUID;

public record CancelEventResponse(UUID eventId, int ordersExpired, int itemsToRefund) {}
