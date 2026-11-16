package com.ticketya.order.service;

import com.ticketya.order.client.InventoryPort;
import com.ticketya.order.exception.OrderException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Libera un hold DESPUÉS de haber persistido el nuevo estado de la orden. Si falla no es grave:
 * inventory expira el hold por TTL y su job lo libera. Por eso es "best effort".
 */
@Component
public class HoldReleaser {

    private static final Logger log = LoggerFactory.getLogger(HoldReleaser.class);

    private final InventoryPort inventory;

    public HoldReleaser(InventoryPort inventory) {
        this.inventory = inventory;
    }

    public void releaseQuietly(UUID holdId) {
        if (holdId == null) {
            return;
        }
        try {
            inventory.releaseHold(holdId);
        } catch (OrderException e) {
            log.warn("no se pudo liberar el hold {} (expirará por TTL): {}", holdId, e.getMessage());
        }
    }
}
