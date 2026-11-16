package com.ticketya.order.service;

import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.entity.PurchaseOrder;
import com.ticketya.order.repository.OrderRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Expira órdenes PENDING_PAYMENT con la reserva vencida (sin pago en curso) y libera cuota y hold. */
@Service
public class ExpirePendingOrdersService {

    private static final Logger log = LoggerFactory.getLogger(ExpirePendingOrdersService.class);

    private final OrderRepository orderRepo;
    private final QuotaService quota;
    private final HoldReleaser holdReleaser;
    private final OrderProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ExpirePendingOrdersService(OrderRepository orderRepo, QuotaService quota, HoldReleaser holdReleaser,
                                      OrderProperties props, TransactionTemplate tx, Clock clock) {
        this.orderRepo = orderRepo;
        this.quota = quota;
        this.holdReleaser = holdReleaser;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    /** Procesa un lote; devuelve cuántas órdenes expiró. */
    public int expirarLote() {
        List<UUID> holds = tx.execute(status -> {
            List<PurchaseOrder> batch = orderRepo.lockExpiredBatch(clock.instant(), props.jobs().expire().batchSize());
            batch.forEach(o -> {
                o.expire();
                quota.release(o);
            });
            return batch.stream().map(PurchaseOrder::getHoldId).toList();
        });
        holds.forEach(holdReleaser::releaseQuietly);
        if (!holds.isEmpty()) {
            log.info("órdenes expiradas: {}", holds.size());
        }
        return holds.size();
    }
}
