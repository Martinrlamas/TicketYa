package com.ticketya.order.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Toma las solicitudes PROCESSING vencidas (nuevas o con backoff cumplido) y las procesa. */
@Service
public class RefundRecoveryService {

    private static final int MAX_ROUNDS = 50;

    private final RefundTransactions txOps;
    private final RefundProcessor processor;

    public RefundRecoveryService(RefundTransactions txOps, RefundProcessor processor) {
        this.txOps = txOps;
        this.processor = processor;
    }

    /** Devuelve cuántas solicitudes procesó. Las que fallan quedan con backoff, así que no se re-toman en la misma corrida. */
    public int runOnce() {
        int total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<UUID> ids = txOps.claimDue();
            if (ids.isEmpty()) {
                break;
            }
            ids.forEach(processor::process);
            total += ids.size();
        }
        return total;
    }
}
