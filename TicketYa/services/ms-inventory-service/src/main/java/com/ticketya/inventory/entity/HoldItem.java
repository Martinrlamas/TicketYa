package com.ticketya.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Un ítem por asiento retenido (quantity=1, seatId) o uno por zona (quantity=n, seatId=null). */
@Entity
@Table(name = "hold_item")
public class HoldItem {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private UUID holdId;
    private UUID sectorId;
    private UUID seatId;
    private int quantity;

    protected HoldItem() {}

    private HoldItem(UUID holdId, UUID sectorId, UUID seatId, int quantity) {
        this.holdId = holdId;
        this.sectorId = sectorId;
        this.seatId = seatId;
        this.quantity = quantity;
    }

    public static HoldItem seat(UUID holdId, UUID sectorId, UUID seatId) {
        return new HoldItem(holdId, sectorId, seatId, 1);
    }

    public static HoldItem zone(UUID holdId, UUID sectorId, int quantity) {
        return new HoldItem(holdId, sectorId, null, quantity);
    }

    public UUID getId() { return id; }
    public UUID getHoldId() { return holdId; }
    public UUID getSectorId() { return sectorId; }
    public UUID getSeatId() { return seatId; }
    public int getQuantity() { return quantity; }
    public boolean isSeat() { return seatId != null; }
}
