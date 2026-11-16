package com.ticketya.order.job;

import com.ticketya.order.service.RefundRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Ejecuta las devoluciones aprobadas y reintenta (con backoff) las que fallaron de forma transitoria. */
@Component
@ConditionalOnProperty(name = "order.refunds.recovery-enabled", havingValue = "true", matchIfMissing = true)
public class RefundRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(RefundRecoveryJob.class);
    private final RefundRecoveryService service;

    public RefundRecoveryJob(RefundRecoveryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${order.refunds.recovery-rate-ms:5000}")
    public void run() {
        try {
            service.runOnce();
        } catch (RuntimeException e) {
            log.error("falló el job de recuperación de devoluciones", e);
        }
    }
}
