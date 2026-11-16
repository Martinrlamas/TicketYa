package com.ticketya.order.notification;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class LogBuyerNotifier implements BuyerNotifier {

    private static final Logger log = LoggerFactory.getLogger(LogBuyerNotifier.class);

    @Override
    public void eventCancelled(UUID customerId, UUID orderId, UUID eventId, BigDecimal refundedAmount, String currency) {
        log.info("BUYER_NOTICE customerId={} orderId={} eventId={} refunded={} {} correlationId={} "
                        + "mensaje=\"El evento fue cancelado; se reintegró el importe de tu entrada\"",
                customerId, orderId, eventId, refundedAmount, currency, MDC.get("correlationId"));
    }
}
