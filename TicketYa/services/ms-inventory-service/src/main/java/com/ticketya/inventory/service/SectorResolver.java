package com.ticketya.inventory.service;

import com.ticketya.inventory.dto.request.StockItemRequest;
import com.ticketya.inventory.entity.EventSector;
import com.ticketya.inventory.entity.SectorType;
import com.ticketya.inventory.exception.InventoryException;
import com.ticketya.inventory.repository.EventSectorRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resuelve y valida los sectores referenciados por un request contra los inicializados para el evento. */
@Component
class SectorResolver {

    private final EventSectorRepository sectorRepo;

    SectorResolver(EventSectorRepository sectorRepo) {
        this.sectorRepo = sectorRepo;
    }

    Map<UUID, EventSector> forEvent(UUID eventId) {
        Map<UUID, EventSector> map = new LinkedHashMap<>();
        sectorRepo.findByEventIdOrderByPositionAsc(eventId).forEach(s -> map.put(s.getSectorId(), s));
        return map;
    }

    /** 404 si el sector no existe; 400 si se piden asientos en una zona o cantidad en un sector numerado. */
    EventSector require(Map<UUID, EventSector> sectors, StockItemRequest item) {
        EventSector sector = sectors.get(item.sectorId());
        if (sector == null) {
            throw InventoryException.noEncontrado("Sector inexistente en el inventario: " + item.sectorId());
        }
        if (item.isNumbered() && sector.getType() != SectorType.NUMBERED) {
            throw InventoryException.validacion("El sector " + item.sectorId() + " es GENERAL: usar quantity");
        }
        if (!item.isNumbered() && sector.getType() != SectorType.GENERAL) {
            throw InventoryException.validacion("El sector " + item.sectorId() + " es NUMBERED: usar seatIds");
        }
        return sector;
    }
}
