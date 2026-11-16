package com.ticketya.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Cancelación de un evento recibida de catalog (una fila por evento) y el avance de su procesamiento. */
@Entity
@Table(name = "event_cancellation")
public class EventCancellation {

    @Id
    private UUID eventId;
    private String reason;
    private int ordersExpired;
    private int itemsToRefund;
    private boolean inventoryClosed;
    private boolean ticketsInvalidated;
    private boolean settled;
    private Instant createdAt;
    private Instant updatedAt;

    protected EventCancellation() {}

    public void addProgress(int expired, int items, Instant now) {
        this.ordersExpired += expired;
        this.itemsToRefund += items;
        this.updatedAt = now;
    }

    public void markInventoryClosed(Instant now) {
        this.inventoryClosed = true;
        this.updatedAt = now;
    }

    public void markTicketsInvalidated(Instant now) {
        this.ticketsInvalidated = true;
        this.updatedAt = now;
    }

    public void markSettled(Instant now) {
        this.settled = true;
        this.updatedAt = now;
    }

    public UUID getEventId() { return eventId; }
    public String getReason() { return reason; }
    public int getOrdersExpired() { return ordersExpired; }
    public int getItemsToRefund() { return itemsToRefund; }
    public boolean isInventoryClosed() { return inventoryClosed; }
    public boolean isTicketsInvalidated() { return ticketsInvalidated; }
    public boolean isSettled() { return settled; }
}
