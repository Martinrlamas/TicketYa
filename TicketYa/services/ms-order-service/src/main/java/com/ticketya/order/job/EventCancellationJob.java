package com.ticketya.order.job;

import com.ticketya.order.service.CancelEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Termina las cancelaciones de evento que quedaron a medias (inventario o tickets caídos, pagos en curso). */
@Component
@ConditionalOnProperty(name = "order.cancellation.enabled", havingValue = "true", matchIfMissing = true)
public class EventCancellationJob {

    private static final Logger log = LoggerFactory.getLogger(EventCancellationJob.class);
    private final CancelEventService service;

    public EventCancellationJob(CancelEventService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${order.cancellation.rate-ms:15000}")
    public void run() {
        try {
            service.avanzarTodasLasPendientes();
        } catch (RuntimeException e) {
            log.error("falló el job de cancelación de eventos", e);
        }
    }
}
