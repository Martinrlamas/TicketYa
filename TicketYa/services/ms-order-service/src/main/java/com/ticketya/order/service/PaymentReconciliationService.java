package com.ticketya.order.service;

import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.repository.PayAttemptRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reanuda las sagas de pago que quedaron a medias (sin novedades hace más de {@code min-age}). */
@Service
public class PaymentReconciliationService {

    private final PayAttemptRepository attemptRepo;
    private final PayOrderService payService;
    private final OrderProperties props;
    private final Clock clock;

    public PaymentReconciliationService(PayAttemptRepository attemptRepo, PayOrderService payService,
                                        OrderProperties props, Clock clock) {
        this.attemptRepo = attemptRepo;
        this.payService = payService;
        this.props = props;
        this.clock = clock;
    }

    public int reconcile() {
        List<UUID> stale = attemptRepo.findStaleInFlightOrderIds(clock.instant().minus(props.jobs().reconcile().minAge()));
        stale.forEach(payService::resume);               // cada resume es idempotente y no propaga errores de negocio
        return stale.size();
    }
}
