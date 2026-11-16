package com.ticketya.inventory.service;

import com.ticketya.inventory.dto.request.SoldReleaseRequest;
import com.ticketya.inventory.dto.request.StockItemRequest;
import com.ticketya.inventory.dto.response.SoldReleaseResponse;
import com.ticketya.inventory.entity.EventSector;
import com.ticketya.inventory.event.StockChanged;
import com.ticketya.inventory.exception.InventoryException;
import com.ticketya.inventory.repository.EventInventoryRepository;
import com.ticketya.inventory.repository.EventSeatRepository;
import com.ticketya.inventory.repository.SoldReleaseRepository;
import com.ticketya.inventory.repository.ZoneStockRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Devolución de lugares vendidos (reintegros / cancelaciones), idempotente por releaseId. */
@Service
public class SoldReleaseService {

    private final EventInventoryRepository inventoryRepo;
    private final EventSeatRepository seatRepo;
    private final ZoneStockRepository zoneRepo;
    private final SoldReleaseRepository releaseRepo;
    private final SectorResolver sectorResolver;
    private final ApplicationEventPublisher events;

    public SoldReleaseService(EventInventoryRepository inventoryRepo, EventSeatRepository seatRepo,
                              ZoneStockRepository zoneRepo, SoldReleaseRepository releaseRepo,
                              SectorResolver sectorResolver, ApplicationEventPublisher events) {
        this.inventoryRepo = inventoryRepo;
        this.seatRepo = seatRepo;
        this.zoneRepo = zoneRepo;
        this.releaseRepo = releaseRepo;
        this.sectorResolver = sectorResolver;
        this.events = events;
    }

    @Transactional
    public SoldReleaseResponse release(UUID eventId, SoldReleaseRequest req) {
        if (!inventoryRepo.existsById(eventId)) {
            throw InventoryException.noEncontrado("El evento " + eventId + " no tiene inventario");
        }

        // Reclamo atómico del releaseId; si ya estaba aplicada se devuelve el resultado original sin tocar nada.
        if (releaseRepo.insertIfAbsent(req.releaseId(), eventId) == 0) {
            int previous = releaseRepo.findById(req.releaseId()).orElseThrow().getReleasedCount();
            return new SoldReleaseResponse(req.releaseId(), previous);
        }

        Map<UUID, EventSector> sectors = sectorResolver.forEvent(eventId);
        int released = 0;
        for (StockItemRequest item : req.items()) {
            sectorResolver.require(sectors, item);
            if (item.isNumbered()) {
                List<UUID> ids = item.seatIds().stream().distinct().sorted().toList();
                if (seatRepo.releaseSold(eventId, item.sectorId(), ids) != ids.size()) {
                    throw InventoryException.estadoInvalido("Algún asiento del sector " + item.sectorId() + " no está SOLD");
                }
                released += ids.size();
            } else {
                if (zoneRepo.releaseSold(eventId, item.sectorId(), item.quantity()) != 1) {
                    throw InventoryException.estadoInvalido(
                            "La zona " + item.sectorId() + " no tiene " + item.quantity() + " lugares vendidos");
                }
                released += item.quantity();
            }
        }
        releaseRepo.updateCount(req.releaseId(), released);
        events.publishEvent(new StockChanged(eventId));
        return new SoldReleaseResponse(req.releaseId(), released);
    }
}
