package com.ticketya.order.service;

import com.ticketya.order.client.InventoryPort;
import com.ticketya.order.entity.OrderItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resultado de planificar un pedido: un ítem por lugar con su precio vigente y lo que hay que reservar en inventory.
 * La etiqueta del asiento ("Platea A-5") se completa con {@link #snapshots(Map)} cuando inventory responde el hold.
 */
public record OrderPlan(List<PlannedItem> items, String currency, List<InventoryPort.StockItem> stockItems) {

    public record PlannedItem(UUID sectorId, UUID seatId, String sectorName, BigDecimal unitPrice, String currency) {}

    public int quantity() {
        return items.size();
    }

    /** "Platea A-5" para asientos; el nombre del sector ("Campo") para lugares sin asiento. */
    public List<OrderItem.Snapshot> snapshots(Map<UUID, String> seatLabels) {
        return items.stream()
                .map(i -> new OrderItem.Snapshot(i.sectorId(), i.seatId(), label(i, seatLabels), i.unitPrice(), i.currency()))
                .toList();
    }

    private static String label(PlannedItem item, Map<UUID, String> seatLabels) {
        String name = item.sectorName() != null ? item.sectorName() : "General";
        String seat = item.seatId() == null ? null : seatLabels.get(item.seatId());
        return seat == null ? name : name + " " + seat;
    }
}
