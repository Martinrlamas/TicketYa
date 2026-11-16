package com.ticketya.payment.gateway;

import java.math.BigDecimal;

public record GatewayRefundRequest(String idempotencyKey, String paymentGatewayRef, BigDecimal amount) {}
