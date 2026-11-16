package com.ticketya.order.notification;

import java.math.BigDecimal;
import java.util.UUID;

/** Aviso a compradores afectados por la cancelación de un evento. En E1 solo se registra en el log; en E2 será real. */
public interface BuyerNotifier {

    void eventCancelled(UUID customerId, UUID orderId, UUID eventId, BigDecimal refundedAmount, String currency);
}
