package com.ticketya.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "event_sector")
@IdClass(EventSector.Key.class)
public class EventSector {

    @Id
    private UUID eventId;
    @Id
    private UUID sectorId;

    @Enumerated(EnumType.STRING)
    private SectorType type;

    private String name;
    private int position;

    protected EventSector() {}

    public UUID getEventId() { return eventId; }
    public UUID getSectorId() { return sectorId; }
    public SectorType getType() { return type; }
    public String getName() { return name; }
    public int getPosition() { return position; }

    /** Clave compuesta (@IdClass). Clase clásica con constructor vacío: lo exige JPA. */
    public static class Key implements Serializable {
        private UUID eventId;
        private UUID sectorId;

        public Key() {}

        public Key(UUID eventId, UUID sectorId) {
            this.eventId = eventId;
            this.sectorId = sectorId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other
                    && Objects.equals(eventId, other.eventId) && Objects.equals(sectorId, other.sectorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, sectorId);
        }
    }
}
