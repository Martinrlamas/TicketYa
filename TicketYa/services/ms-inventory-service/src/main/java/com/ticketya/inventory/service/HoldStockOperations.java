package com.ticketya.inventory.service;

import com.ticketya.inventory.entity.Hold;
import com.ticketya.inventory.entity.HoldItem;
import com.ticketya.inventory.repository.EventSeatRepository;
import com.ticketya.inventory.repository.HoldItemRepository;
import com.ticketya.inventory.repository.ZoneStockRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aplica sobre el stock lo que un hold ya retiene. Lo comparten HoldService y HoldExpirationService.
 * No abre transacción: usa la del llamador (que ya tiene el hold bloqueado con FOR UPDATE).
 */
@Component
class HoldStockOperations {

    private static final Logger log = LoggerFactory.getLogger(HoldStockOperations.class);

    private final HoldItemRepository itemRepo;
    private final EventSeatRepository seatRepo;
    private final ZoneStockRepository zoneRepo;

    HoldStockOperations(HoldItemRepository itemRepo, EventSeatRepository seatRepo, ZoneStockRepository zoneRepo) {
        this.itemRepo = itemRepo;
        this.seatRepo = seatRepo;
        this.zoneRepo = zoneRepo;
    }

    /** HELD -> SOLD y held -> sold. Si no coincide lo esperado hay una invariante rota: se aborta todo. */
    void confirm(Hold hold) {
        List<HoldItem> items = itemRepo.findByHoldId(hold.getId());
        long expectedSeats = items.stream().filter(HoldItem::isSeat).count();
        if (expectedSeats > 0) {
            int sold = seatRepo.markSold(hold.getId());
            if (sold != expectedSeats) {
                throw new IllegalStateException("hold " + hold.getId() + ": se esperaban " + expectedSeats
                        + " asientos HELD y se vendieron " + sold);
            }
        }
        for (HoldItem item : items) {
            if (!item.isSeat() && zoneRepo.confirmHold(hold.getEventId(), item.getSectorId(), item.getQuantity()) != 1) {
                throw new IllegalStateException("hold " + hold.getId() + ": zona " + item.getSectorId()
                        + " no tenía " + item.getQuantity() + " lugares retenidos");
            }
        }
    }

    /** HELD -> AVAILABLE y held = held - n. Idempotente: solo toca lo que sigue retenido por este hold. */
    void release(Hold hold) {
        List<HoldItem> items = itemRepo.findByHoldId(hold.getId());
        if (items.stream().anyMatch(HoldItem::isSeat)) {
            seatRepo.releaseHeld(hold.getId());
        }
        for (HoldItem item : items) {
            if (!item.isSeat() && zoneRepo.releaseHold(hold.getEventId(), item.getSectorId(), item.getQuantity()) != 1) {
                // no se lanza: un hold inconsistente no debe bloquear el job de expiración del resto
                log.error("hold {}: no se pudo devolver {} lugares a la zona {}", hold.getId(), item.getQuantity(), item.getSectorId());
            }
        }
    }
}
