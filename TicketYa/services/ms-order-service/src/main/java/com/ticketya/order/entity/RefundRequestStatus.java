package com.ticketya.order.entity;

import java.util.EnumSet;
import java.util.Set;

public enum RefundRequestStatus {
    PENDING, REJECTED, APPROVED, PROCESSING, COMPLETED, FAILED;

    /** Estados en los que la solicitud todavía "ocupa" al ítem (índice único parcial). */
    public static final Set<RefundRequestStatus> ACTIVE = EnumSet.of(PENDING, APPROVED, PROCESSING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
