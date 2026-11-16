package com.ticketya.inventory.service;

import com.ticketya.inventory.entity.Hold;
import com.ticketya.inventory.event.StockChanged;
import com.ticketya.inventory.repository.HoldRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bean aparte del job: si {@code @Transactional} estuviera en el mismo bean que {@code @Scheduled}
 * la auto-invocación lo ignoraría.
 */
@Service
public class HoldExpirationService {

    private final HoldRepository holdRepo;
    private final HoldStockOperations stockOps;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public HoldExpirationService(HoldRepository holdRepo, HoldStockOperations stockOps,
                                 ApplicationEventPublisher events, Clock clock) {
        this.holdRepo = holdRepo;
        this.stockOps = stockOps;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Expira hasta {@code batchSize} holds vencidos. FOR UPDATE SKIP LOCKED: las filas que otra instancia
     * (o un confirm en curso) tiene bloqueadas se saltean. Devuelve cuántos expiró.
     */
    @Transactional
    public int expirarLote(int batchSize) {
        List<Hold> expired = holdRepo.lockExpiredBatch(clock.instant(), batchSize);
        for (Hold hold : expired) {
            stockOps.release(hold);
            hold.expire();
        }
        expired.stream().map(Hold::getEventId).distinct()
                .forEach(eventId -> events.publishEvent(new StockChanged(eventId)));
        return expired.size();
    }
}
