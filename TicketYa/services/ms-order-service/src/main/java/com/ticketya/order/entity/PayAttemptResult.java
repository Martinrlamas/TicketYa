package com.ticketya.order.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estado persistido de la saga de pago de una orden. Permite reanudar tras una falla parcial:
 * <pre>
 * PENDING --> APPROVED --> COMPLETED            (camino feliz)
 *    |           |
 *    |           +--> COMPENSATING --> COMPENSATED   (cobrado pero no se pudo vender: reintegro)
 *    +--> REJECTED
 *    +--> ABANDONED  (sin rastro en payment-service; no cuenta como "en curso")
 * </pre>
 */
public enum PayAttemptResult {
    PENDING,
    APPROVED,
    COMPENSATING,
    COMPLETED,
    REJECTED,
    COMPENSATED,
    ABANDONED;

    /** Intentos que todavía requieren acción: bloquean la expiración y otros pagos de la misma orden. */
    public static final Set<PayAttemptResult> IN_FLIGHT = EnumSet.of(PENDING, APPROVED, COMPENSATING);

    public boolean isInFlight() {
        return IN_FLIGHT.contains(this);
    }
}
