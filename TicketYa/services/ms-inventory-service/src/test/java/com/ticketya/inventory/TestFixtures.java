package com.ticketya.inventory;

import com.ticketya.inventory.dto.request.InitInventoryRequest;
import com.ticketya.inventory.dto.request.InitInventoryRequest.RowInit;
import com.ticketya.inventory.dto.request.InitInventoryRequest.SectorInit;
import com.ticketya.inventory.dto.request.CreateHoldRequest;
import com.ticketya.inventory.dto.request.StockItemRequest;
import com.ticketya.inventory.entity.SectorType;
import java.util.List;
import java.util.UUID;

public final class TestFixtures {

    private TestFixtures() {}

    public static SectorInit numbered(UUID sectorId, String name, RowInit... rows) {
        return new SectorInit(sectorId, SectorType.NUMBERED, name, List.of(rows), null);
    }

    public static SectorInit general(UUID sectorId, String name, int capacity) {
        return new SectorInit(sectorId, SectorType.GENERAL, name, null, capacity);
    }

    public static InitInventoryRequest init(SectorInit... sectors) {
        return new InitInventoryRequest(List.of(sectors));
    }

    public static CreateHoldRequest seatHold(UUID eventId, UUID orderId, UUID sectorId, UUID... seatIds) {
        return new CreateHoldRequest(eventId, orderId, null,
                List.of(new StockItemRequest(sectorId, List.of(seatIds), null)));
    }

    public static CreateHoldRequest zoneHold(UUID eventId, UUID orderId, UUID sectorId, int quantity) {
        return new CreateHoldRequest(eventId, orderId, null,
                List.of(new StockItemRequest(sectorId, null, quantity)));
    }
}
