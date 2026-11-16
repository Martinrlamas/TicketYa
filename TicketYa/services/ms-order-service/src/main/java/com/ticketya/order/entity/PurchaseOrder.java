package com.ticketya.order.entity;

import com.ticketya.order.exception.OrderException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * Orden de compra. Las reglas de la máquina de estados viven aquí:
 * <pre>
 * PENDING_PAYMENT -> CONFIRMED | PAYMENT_REJECTED | EXPIRED | CANCELLED | FAILED_REFUNDED
 * CONFIRMED       -> CANCELLED_BY_EVENT (change 06)
 * </pre>
 * Los ítems llevan el precio vigente al crear la orden (snapshot).
 */
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

    @Id
    private UUID id;
    private UUID customerId;
    private UUID eventId;
    private Instant eventStartsAt;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    private String currency;
    private UUID holdId;
    private Instant holdExpiresAt;
    private String idempotencyKey;
    private String requestHash;
    private UUID paymentId;
    private String paymentMethod;
    private String paymentLast4;

    @Version
    private Long version;

    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("position")
    private List<OrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {}

    public static PurchaseOrder create(UUID id, UUID customerId, UUID eventId, Instant eventStartsAt, String currency,
                               UUID holdId, Instant holdExpiresAt, String idempotencyKey, String requestHash,
                               List<OrderItem.Snapshot> snapshots, Instant now) {
        PurchaseOrder o = new PurchaseOrder();
        o.id = id;
        o.customerId = customerId;
        o.eventId = eventId;
        o.eventStartsAt = eventStartsAt;
        o.status = OrderStatus.PENDING_PAYMENT;
        o.currency = currency;
        o.holdId = holdId;
        o.holdExpiresAt = holdExpiresAt;
        o.idempotencyKey = idempotencyKey;
        o.requestHash = requestHash;
        o.createdAt = now;
        int position = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem.Snapshot s : snapshots) {
            o.items.add(OrderItem.of(o, position++, s));
            total = total.add(s.unitPrice());
        }
        o.total = total.setScale(2);
        return o;
    }

    // ---------- máquina de estados ----------

    /** Guarda el medio de pago aprobado. La orden sigue PENDING_PAYMENT hasta vender y emitir. */
    public void registrarPagoAprobado(UUID paymentId, String method, String last4) {
        exigirPendiente("registrar un pago");
        this.paymentId = paymentId;
        this.paymentMethod = method;
        this.paymentLast4 = last4;
    }

    public void confirm() {
        exigirPendiente("confirmar");
        this.status = OrderStatus.CONFIRMED;
        this.holdExpiresAt = null;                 // ya no hay reserva: la venta está confirmada
    }

    public void rechazarPago() {
        exigirPendiente("rechazar el pago");
        this.status = OrderStatus.PAYMENT_REJECTED;
    }

    public void expire() {
        exigirPendiente("expirar");
        this.status = OrderStatus.EXPIRED;
    }

    public void cancel() {
        exigirPendiente("cancelar");
        this.status = OrderStatus.CANCELLED;
    }

    public void fallarReintegrado() {
        exigirPendiente("marcar como FAILED_REFUNDED");
        this.status = OrderStatus.FAILED_REFUNDED;
    }

    /** Cancelación del evento: la orden confirmada pasa a CANCELLED_BY_EVENT (sus ítems se reintegran aparte). */
    public void cancelarPorEvento() {
        if (status != OrderStatus.CONFIRMED) {
            throw OrderException.estadoInvalido("No se puede cancelar por evento: la orden está " + status);
        }
        this.status = OrderStatus.CANCELLED_BY_EVENT;
    }

    public boolean isConfirmed() { return status == OrderStatus.CONFIRMED; }

    public OrderItem findItem(UUID itemId) {
        return items.stream().filter(i -> i.getId().equals(itemId)).findFirst().orElse(null);
    }

    private void exigirPendiente(String action) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw OrderException.estadoInvalido("No se puede " + action + ": la orden está " + status);
        }
    }

    // ---------- consultas de dominio ----------

    public boolean isPending() { return status == OrderStatus.PENDING_PAYMENT; }

    public boolean esDuenio(UUID userId) { return customerId.equals(userId); }

    public boolean reservaExpirada(Instant now) { return holdExpiresAt != null && !holdExpiresAt.isAfter(now); }

    /** Cantidad de lugares que esta orden consume de la cuota del cliente. */
    public int quantity() { return items.size(); }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getEventId() { return eventId; }
    public Instant getEventStartsAt() { return eventStartsAt; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public String getCurrency() { return currency; }
    public UUID getHoldId() { return holdId; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getPaymentId() { return paymentId; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentLast4() { return paymentLast4; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }
}
