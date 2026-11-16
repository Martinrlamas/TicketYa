package com.ticketya.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Registro de liberaciones de lugares vendidos ya aplicadas (idempotencia por releaseId). */
@Entity
@Table(name = "sold_release")
public class SoldRelease {

    @Id
    private UUID releaseId;
    private UUID eventId;
    private int releasedCount;

    protected SoldRelease() {}

    public UUID getEventId() { return eventId; }
    public int getReleasedCount() { return releasedCount; }
}
