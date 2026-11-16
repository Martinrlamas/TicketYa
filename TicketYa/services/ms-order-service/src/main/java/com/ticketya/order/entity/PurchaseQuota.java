package com.ticketya.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

/** Entradas consumidas por un cliente para un evento. Solo se modifica con UPDATE condicionales. */
@Entity
@Table(name = "purchase_quota")
@IdClass(PurchaseQuotaId.class)
public class PurchaseQuota {

    @Id
    private UUID customerId;
    @Id
    private UUID eventId;

    private int used;

    protected PurchaseQuota() {}

    public UUID getCustomerId() { return customerId; }
    public UUID getEventId() { return eventId; }
}
