package com.ticketya.catalog.client;

import java.util.List;
import java.util.UUID;

import com.ticketya.catalog.client.dto.InventorySectorDto;

/** Puerto de salida hacia inventory-service (en E2 puede pasar a mensajería sin tocar el service). */
public interface InventoryClient {

    /** Idempotente en el servidor: reintentar no duplica asientos. */
    void initialize(UUID eventId, List<InventorySectorDto> sectors);

    /** Bloquea nuevos holds. Idempotente. */
    void close(UUID eventId);
}
