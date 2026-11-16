package com.ticketya.order.service;

import com.ticketya.order.config.OrderProperties;
import com.ticketya.order.exception.OrderException;
import com.ticketya.platform.error.ErrorCode;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reintenta SOLO fallas transitorias (UPSTREAM_UNAVAILABLE). Es seguro porque las operaciones que
 * reintenta (confirm del hold, emisión de entradas) son idempotentes en el servicio destino.
 */
@Component
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final OrderProperties props;

    public RetryExecutor(OrderProperties props) {
        this.props = props;
    }

    public <T> T run(String what, Supplier<T> action) {
        int max = Math.max(1, props.retry().maxAttempts());
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (OrderException e) {
                if (!e.hasCode(ErrorCode.UPSTREAM_UNAVAILABLE) || attempt >= max) {
                    throw e;
                }
                log.warn("{} falló (intento {}/{}), se reintenta", what, attempt, max);
                pause();
            }
        }
    }

    public void run(String what, Runnable action) {
        run(what, () -> {
            action.run();
            return null;
        });
    }

    private void pause() {
        long millis = props.retry().backoff().toMillis();
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OrderException.servicioNoDisponible("Interrumpido durante el reintento");
        }
    }
}
