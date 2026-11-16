package com.ticketya.order.job;

import com.ticketya.order.service.PaymentReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "order.jobs.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class ReconcilePaymentsJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcilePaymentsJob.class);
    private final PaymentReconciliationService service;

    public ReconcilePaymentsJob(PaymentReconciliationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${order.jobs.reconcile.rate-ms:30000}")
    public void run() {
        try {
            service.reconcile();
        } catch (RuntimeException e) {
            log.error("falló el job de reconciliación de pagos", e);
        }
    }
}
