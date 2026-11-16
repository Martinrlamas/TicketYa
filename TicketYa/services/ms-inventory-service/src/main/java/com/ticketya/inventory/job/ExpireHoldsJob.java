package com.ticketya.inventory.job;

import com.ticketya.inventory.config.InventoryProperties;
import com.ticketya.inventory.service.HoldExpirationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Libera los holds vencidos cada 15 s. No es crítico para la corrección (confirm revalida expires_at)
 * y es seguro con N instancias gracias a SKIP LOCKED.
 */
@Component
@ConditionalOnProperty(name = "inventory.expire-job.enabled", havingValue = "true", matchIfMissing = true)
public class ExpireHoldsJob {

    private static final Logger log = LoggerFactory.getLogger(ExpireHoldsJob.class);

    private final HoldExpirationService expirationService;
    private final InventoryProperties props;

    public ExpireHoldsJob(HoldExpirationService expirationService, InventoryProperties props) {
        this.expirationService = expirationService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${inventory.expire-job.rate-ms:15000}")
    public void run() {
        int batch = props.expireJob().batchSize();
        try {
            int n;
            do {
                n = expirationService.expirarLote(batch);
                if (n > 0) {
                    log.info("holds expirados: {}", n);
                }
            } while (n == batch);
        } catch (RuntimeException ex) {
            log.error("falló la expiración de holds, se reintenta en el próximo ciclo", ex);
        }
    }
}
