package com.ticketya.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

/** Contador de una zona no numerada. Solo se modifica con UPDATE condicionales atómicos. */
@Entity
@Table(name = "zone_stock")
@IdClass(ZoneStockId.class)
public class ZoneStock {

    @Id
    private UUID eventId;
    @Id
    private UUID sectorId;

    private int capacity;
    private int held;
    private int sold;
    private long version;

    protected ZoneStock() {}

    public UUID getEventId() { return eventId; }
    public UUID getSectorId() { return sectorId; }
    public int getCapacity() { return capacity; }
    public int available() { return capacity - held - sold; }
}
