package com.ticketya.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Estado del inventario de un evento. Se crea con {@code insertIfAbsent} (idempotente). */
@Entity
@Table(name = "event_inventory")
public class EventInventory {

    @Id
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    private InventoryStatus status;

    private boolean soldOutNotified;

    protected EventInventory() {}

    public UUID getEventId() { return eventId; }
    public InventoryStatus getStatus() { return status; }
    public boolean isClosed() { return status == InventoryStatus.CLOSED; }

    /** Idempotente: cerrar un inventario ya cerrado no hace nada. */
    public void close() { this.status = InventoryStatus.CLOSED; }
}
