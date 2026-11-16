package com.ticketya.order.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Puerto hacia ticket-service. */
public interface TicketPort {

    /**
     * Emite una entrada por ítem. Idempotente por orderItemId: volver a llamar devuelve las entradas ya
     * emitidas, así que también sirve para consultarlas.
     */
    List<TicketInfo> issue(UUID orderId, UUID eventId, UUID customerId, List<TicketItem> items);

    /** Idempotente con el mismo motivo (REFUNDED | CANCELLED). 409 INVALID_STATE_TRANSITION si la entrada ya fue usada. */
    TicketInfo invalidate(UUID ticketId, String reason);

    /** Invalida todas las entradas VALID del evento con motivo CANCELLED. Devuelve cuántas invalidó. */
    int invalidateEvent(UUID eventId, String reason);

    record TicketItem(UUID orderItemId, UUID sectorId, UUID seatId, String seatLabel) {}

    record TicketInfo(UUID ticketId, UUID orderItemId, UUID eventId, UUID sectorId, UUID seatId,
                      String seatLabel, String qrCode, String status, Instant issuedAt) {}
}
