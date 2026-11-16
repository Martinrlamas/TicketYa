package com.ticketya.inventory.entity;

import com.ticketya.inventory.exception.InventoryException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva temporal. Se inserta con {@code HoldRepository.insertIfAbsent} (idempotente por orderId).
 * Las reglas de transición viven aquí: ACTIVE -> CONFIRMED | RELEASED | EXPIRED.
 */
@Entity
@Table(name = "hold")
public class Hold {

    @Id
    private UUID id;
    private UUID eventId;
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    private HoldStatus status;

    private Instant expiresAt;
    private Instant createdAt;

    protected Hold() {}

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getOrderId() { return orderId; }
    public HoldStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void confirm() {
        exigirActivo("confirmar");
        this.status = HoldStatus.CONFIRMED;
    }

    public void release() {
        exigirActivo("liberar");
        this.status = HoldStatus.RELEASED;
    }

    public void expire() {
        exigirActivo("expirar");
        this.status = HoldStatus.EXPIRED;
    }

    private void exigirActivo(String action) {
        if (status != HoldStatus.ACTIVE) {
            throw InventoryException.estadoInvalido("No se puede " + action + " un hold " + status);
        }
    }
}
