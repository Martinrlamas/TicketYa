package com.ticketya.order.job;

import com.ticketya.order.service.ExpirePendingOrdersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "order.jobs.expire.enabled", havingValue = "true", matchIfMissing = true)
public class ExpirePendingOrdersJob {

    private static final Logger log = LoggerFactory.getLogger(ExpirePendingOrdersJob.class);
    private final ExpirePendingOrdersService service;

    public ExpirePendingOrdersJob(ExpirePendingOrdersService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${order.jobs.expire.rate-ms:30000}")
    public void run() {
        try {
            while (service.expirarLote() > 0) {
                // sigue mientras queden lotes
            }
        } catch (RuntimeException e) {
            log.error("falló el job de expiración de órdenes", e);
        }
    }
}
