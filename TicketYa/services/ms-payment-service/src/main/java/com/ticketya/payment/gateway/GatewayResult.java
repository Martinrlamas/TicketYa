package com.ticketya.payment.gateway;

public record GatewayResult(boolean approved, String gatewayRef) {}
