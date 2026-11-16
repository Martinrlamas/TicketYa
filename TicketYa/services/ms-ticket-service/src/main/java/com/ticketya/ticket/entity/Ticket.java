package com.ticketya.ticket.entity;

import com.ticketya.ticket.exception.TicketException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entrada. En producción se inserta con {@code TicketIssueRepository} (INSERT ... ON CONFLICT para que la
 * emisión sea idempotente y segura bajo concurrencia); {@link #issue} existe para el dominio y las pruebas.
 * Las reglas de transición viven aquí:
 * <pre>VALID -> REFUNDED | CANCELLED (invalidate) · VALID -> USED (marcarUsada, se habilita en E2)</pre>
 */
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    private UUID id;
    private UUID orderId;
    private UUID orderItemId;
    private UUID eventId;
    private UUID customerId;
    private UUID sectorId;
    private UUID seatId;
    private String seatLabel;
    private String qrCode;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    private Instant issuedAt;
    private Instant usedAt;

    protected Ticket() {}

    public static Ticket issue(UUID orderId, UUID orderItemId, UUID eventId, UUID customerId, UUID sectorId,
                               UUID seatId, String seatLabel, String qrCode, Instant now) {
        Ticket t = new Ticket();
        t.id = UUID.randomUUID();
        t.orderId = orderId;
        t.orderItemId = orderItemId;
        t.eventId = eventId;
        t.customerId = customerId;
        t.sectorId = sectorId;
        t.seatId = seatId;
        t.seatLabel = seatLabel;
        t.qrCode = qrCode;
        t.status = TicketStatus.VALID;
        t.issuedAt = now;
        return t;
    }

    /**
     * VALID -> REFUNDED | CANCELLED. Idempotente con el mismo motivo (devuelve false, sin cambios).
     * Una entrada USED no se puede invalidar; una ya invalidada por otro motivo tampoco (estados terminales).
     *
     * @return true si cambió de estado
     */
    public boolean invalidate(InvalidationReason reason) {
        TicketStatus target = reason.targetStatus();
        if (status == target) {
            return false;
        }
        if (status != TicketStatus.VALID) {
            throw TicketException.estadoInvalido("La entrada está " + status);
        }
        this.status = target;
        return true;
    }

    /** VALID -> USED. Lo invocará el control de acceso en E2; el modelo ya lo define. */
    public void marcarUsada(Instant now) {
        if (status != TicketStatus.VALID) {
            throw TicketException.estadoInvalido("La entrada está " + status);
        }
        this.status = TicketStatus.USED;
        this.usedAt = now;
    }

    public boolean esDuenio(UUID userId) {
        return customerId.equals(userId);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderItemId() { return orderItemId; }
    public UUID getEventId() { return eventId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getSectorId() { return sectorId; }
    public UUID getSeatId() { return seatId; }
    public String getSeatLabel() { return seatLabel; }
    public String getQrCode() { return qrCode; }
    public TicketStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getUsedAt() { return usedAt; }
}
