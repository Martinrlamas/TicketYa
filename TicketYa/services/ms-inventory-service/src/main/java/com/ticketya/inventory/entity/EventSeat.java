package com.ticketya.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Asiento numerado. Se inserta por JDBC batch y SOLO cambia de estado mediante los UPDATE condicionales
 * de {@code EventSeatRepository}; por eso no tiene setters ni {@code @Version}.
 */
@Entity
@Table(name = "event_seat")
public class EventSeat {

    @Id
    private UUID id;
    private UUID eventId;
    private UUID sectorId;
    private String rowLabel;
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    private UUID holdId;
    private long version;

    protected EventSeat() {}

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getSectorId() { return sectorId; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public SeatStatus getStatus() { return status; }
    public UUID getHoldId() { return holdId; }

    public String label() { return rowLabel + "-" + seatNumber; }
}
