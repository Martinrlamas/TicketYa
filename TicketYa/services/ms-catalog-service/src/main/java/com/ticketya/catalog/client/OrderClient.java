package com.ticketya.catalog.client;

import java.util.UUID;

/** Puerto de salida hacia order-service. */
public interface OrderClient {

    /** Dispara expiración de órdenes pendientes y reintegros. Idempotente por eventId. */
    void notifyEventCancelled(UUID eventId, String reason);
}
