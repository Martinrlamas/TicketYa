package com.ticketya.order.dto.response;

public record PaymentSummary(String method, String last4, String status) {}
