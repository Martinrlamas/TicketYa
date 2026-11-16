package com.ticketya.inventory.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clave compuesta (@IdClass). Clase clásica con constructor vacío: lo exige JPA. */
public class ZoneStockId implements Serializable {

    private UUID eventId;
    private UUID sectorId;

    public ZoneStockId() {}

    public ZoneStockId(UUID eventId, UUID sectorId) {
        this.eventId = eventId;
        this.sectorId = sectorId;
    }

    public UUID getEventId() { return eventId; }
    public UUID getSectorId() { return sectorId; }

    @Override
    public boolean equals(Object o) {
        return o instanceof ZoneStockId other
                && Objects.equals(eventId, other.eventId) && Objects.equals(sectorId, other.sectorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, sectorId);
    }
}
