package com.ticketya.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Un ítem por lugar (también en zonas generales, con seatId nulo). El precio es un snapshot. */
@Entity
@Table(name = "order_item")
public class OrderItem {

    /** Dato calculado por el planificador antes de crear la orden. */
    public record Snapshot(UUID sectorId, UUID seatId, String seatLabel, BigDecimal unitPrice, String currency) {}

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private PurchaseOrder order;

    private int position;
    private UUID sectorId;
    private UUID seatId;
    private String seatLabel;

    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;

    private String currency;

    @Enumerated(EnumType.STRING)
    private OrderItemStatus status;

    protected OrderItem() {}

    static OrderItem of(PurchaseOrder order, int position, Snapshot s) {
        OrderItem i = new OrderItem();
        i.id = UUID.randomUUID();
        i.order = order;
        i.position = position;
        i.sectorId = s.sectorId();
        i.seatId = s.seatId();
        i.seatLabel = s.seatLabel();
        i.unitPrice = s.unitPrice().setScale(2);
        i.currency = s.currency();
        i.status = OrderItemStatus.ACTIVE;
        return i;
    }

    // ---------- transiciones (las dispara el flujo de devoluciones y la cancelación de evento) ----------

    public void solicitarReintegro() {
        require("solicitar devolución", OrderItemStatus.ACTIVE);
        this.status = OrderItemStatus.REFUND_REQUESTED;
    }

    /** Solicitud rechazada: el ítem vuelve a poder usarse / devolverse. */
    public void restoreActive() {
        require("restaurar", OrderItemStatus.REFUND_REQUESTED);
        this.status = OrderItemStatus.ACTIVE;
    }

    public void marcarReintegrado() {
        if (status == OrderItemStatus.REFUNDED || status == OrderItemStatus.CANCELLED) {
            return;                                  // idempotente
        }
        this.status = OrderItemStatus.REFUNDED;
    }

    public void cancelarPorEvento() {
        if (status == OrderItemStatus.ACTIVE || status == OrderItemStatus.REFUND_REQUESTED) {
            this.status = OrderItemStatus.CANCELLED;
        }
    }

    private void require(String action, OrderItemStatus expected) {
        if (status != expected) {
            throw com.ticketya.order.exception.OrderException.estadoInvalido(
                    "No se puede " + action + ": el ítem está " + status);
        }
    }

    public UUID getId() { return id; }
    public int getPosition() { return position; }
    public UUID getSectorId() { return sectorId; }
    public UUID getSeatId() { return seatId; }
    public String getSeatLabel() { return seatLabel; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrency() { return currency; }
    public OrderItemStatus getStatus() { return status; }
}
