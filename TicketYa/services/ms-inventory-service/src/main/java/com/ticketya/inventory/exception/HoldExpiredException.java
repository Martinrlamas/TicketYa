package com.ticketya.inventory.exception;

import com.ticketya.platform.error.ErrorCode;
import java.time.Instant;

/** 410 HOLD_EXPIRED. Subclase propia para poder usarla en {@code noRollbackFor}. */
public class HoldExpiredException extends InventoryException {

    public HoldExpiredException(Instant expiresAt) {
        super(ErrorCode.HOLD_EXPIRED, "El hold venció a las " + expiresAt);
    }
}
