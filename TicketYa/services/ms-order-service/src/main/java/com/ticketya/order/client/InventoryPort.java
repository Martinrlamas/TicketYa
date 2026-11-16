package com.ticketya.order.client;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Puerto hacia inventory-service (holds y stock). */
public interface InventoryPort {

    /** Todo-o-nada. Errores de negocio pasan tal cual: SEAT_UNAVAILABLE / INSUFFICIENT_CAPACITY / EVENT_NOT_PURCHASABLE (409). */
    HoldInfo createHold(UUID eventId, UUID orderId, int ttlSeconds, List<StockItem> items);

    /** Idempotente. HOLD_EXPIRED (410) si venció; INVALID_STATE_TRANSITION (409) si fue liberado. */
    void confirmHold(UUID holdId);

    /** Idempotente. */
    void releaseHold(UUID holdId);

    /** Devuelve al stock lugares ya vendidos (compensación). Idempotente por releaseId. */
    void releaseSold(UUID eventId, UUID releaseId, List<StockItem> items);

    /** Cierra el inventario del evento: no admite nuevos holds. Idempotente. */
    void closeEvent(UUID eventId);

    record StockItem(UUID sectorId, List<UUID> seatIds, Integer quantity) {

        public static StockItem seats(UUID sectorId, List<UUID> seatIds) {
            return new StockItem(sectorId, seatIds, null);
        }

        public static StockItem zone(UUID sectorId, int quantity) {
            return new StockItem(sectorId, null, quantity);
        }
    }

    /** seatLabels: etiqueta "A-5" de cada asiento retenido (vacío en zonas generales). */
    record HoldInfo(UUID holdId, Instant expiresAt, Map<UUID, String> seatLabels) {}
}
