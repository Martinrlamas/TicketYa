package com.ticketya.inventory.event;

import com.ticketya.inventory.client.CatalogEventStatus;
import com.ticketya.inventory.client.EventStatusNotifier;
import com.ticketya.inventory.entity.SeatStatus;
import com.ticketya.inventory.repository.EventInventoryRepository;
import com.ticketya.inventory.repository.EventSeatRepository;
import com.ticketya.inventory.repository.ZoneStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Corre DESPUÉS del commit de la venta/retención: una falla de catalog nunca revierte stock.
 * El flag {@code sold_out_notified} se reclama con un UPDATE condicional, así que con N instancias
 * solo una notifica cada cambio de estado. Si la notificación falla se revierte el flag y el
 * siguiente cambio de stock reintenta.
 */
@Component
public class SoldOutNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SoldOutNotificationListener.class);

    private final EventSeatRepository seatRepo;
    private final ZoneStockRepository zoneRepo;
    private final EventInventoryRepository inventoryRepo;
    private final EventStatusNotifier notifier;

    public SoldOutNotificationListener(EventSeatRepository seatRepo, ZoneStockRepository zoneRepo,
                                       EventInventoryRepository inventoryRepo, EventStatusNotifier notifier) {
        this.seatRepo = seatRepo;
        this.zoneRepo = zoneRepo;
        this.inventoryRepo = inventoryRepo;
        this.notifier = notifier;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStockChanged(StockChanged event) {
        var eventId = event.eventId();
        // Agotado <=> no queda ningún asiento AVAILABLE ni zona con held+sold < capacity (held cuenta como ocupado)
        boolean soldOut = !seatRepo.existsByEventIdAndStatus(eventId, SeatStatus.AVAILABLE)
                && !zoneRepo.existsAvailable(eventId);
        try {
            if (soldOut) {
                if (inventoryRepo.markSoldOutNotified(eventId) == 1) {
                    notifier.notifyStatus(eventId, CatalogEventStatus.SOLD_OUT);
                }
            } else if (inventoryRepo.clearSoldOutNotified(eventId) == 1) {
                notifier.notifyStatus(eventId, CatalogEventStatus.PUBLISHED);
            }
        } catch (RuntimeException ex) {
            log.warn("no se pudo notificar estado a catalog eventId={}: {}", eventId, ex.getMessage());
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
