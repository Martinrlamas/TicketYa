package com.ticketya.order.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PurchaseQuotaId implements Serializable {

    private UUID customerId;
    private UUID eventId;

    public PurchaseQuotaId() {}

    public PurchaseQuotaId(UUID customerId, UUID eventId) {
        this.customerId = customerId;
        this.eventId = eventId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PurchaseQuotaId other
                && Objects.equals(customerId, other.customerId) && Objects.equals(eventId, other.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, eventId);
    }
}
