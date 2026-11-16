package com.ticketya.inventory.client;

import java.util.UUID;

/**
 * Puerto de salida: notifica a catalog el agotamiento / reapertura de un evento.
 * En E1 el adaptador es REST; en E2 se reemplaza por un publicador de eventos al broker.
 */
public interface EventStatusNotifier {

    /** @throws com.ticketya.inventory.exception.InventoryException (503) si catalog no responde. */
    void notifyStatus(UUID eventId, CatalogEventStatus status);
}
