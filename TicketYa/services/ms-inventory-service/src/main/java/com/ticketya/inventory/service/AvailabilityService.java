package com.ticketya.inventory.service;

import com.ticketya.inventory.dto.response.AvailabilityResponse;
import com.ticketya.inventory.dto.response.AvailabilityResponse.SectorAvailability;
import com.ticketya.inventory.entity.EventSeat;
import com.ticketya.inventory.entity.EventSector;
import com.ticketya.inventory.entity.SectorType;
import com.ticketya.inventory.entity.ZoneStock;
import com.ticketya.inventory.exception.InventoryException;
import com.ticketya.inventory.mapper.AvailabilityMapper;
import com.ticketya.inventory.repository.EventInventoryRepository;
import com.ticketya.inventory.repository.EventSeatRepository;
import com.ticketya.inventory.repository.EventSectorRepository;
import com.ticketya.inventory.repository.ZoneStockRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta pública. Siempre lee de la base (el controller agrega Cache-Control: no-store). */
@Service
public class AvailabilityService {

    private final EventInventoryRepository inventoryRepo;
    private final EventSectorRepository sectorRepo;
    private final EventSeatRepository seatRepo;
    private final ZoneStockRepository zoneRepo;
    private final AvailabilityMapper mapper;
    private final Clock clock;

    public AvailabilityService(EventInventoryRepository inventoryRepo, EventSectorRepository sectorRepo,
                               EventSeatRepository seatRepo, ZoneStockRepository zoneRepo,
                               AvailabilityMapper mapper, Clock clock) {
        this.inventoryRepo = inventoryRepo;
        this.sectorRepo = sectorRepo;
        this.seatRepo = seatRepo;
        this.zoneRepo = zoneRepo;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse get(UUID eventId) {
        if (!inventoryRepo.existsById(eventId)) {
            throw InventoryException.noEncontrado("El evento " + eventId + " no tiene inventario");
        }
        List<EventSector> sectors = sectorRepo.findByEventIdOrderByPositionAsc(eventId);
        Map<UUID, List<EventSeat>> seatsBySector = seatRepo
                .findByEventIdOrderBySectorIdAscRowLabelAscSeatNumberAsc(eventId).stream()
                .collect(Collectors.groupingBy(EventSeat::getSectorId));
        Map<UUID, ZoneStock> zones = zoneRepo.findByEventId(eventId).stream()
                .collect(Collectors.toMap(ZoneStock::getSectorId, Function.identity()));

        List<SectorAvailability> result = new ArrayList<>();
        for (EventSector sector : sectors) {
            if (sector.getType() == SectorType.NUMBERED) {
                result.add(mapper.numbered(sector, seatsBySector.getOrDefault(sector.getSectorId(), List.of())));
            } else {
                result.add(mapper.general(sector, zones.get(sector.getSectorId())));
            }
        }
        return new AvailabilityResponse(eventId, clock.instant(), result);
    }
}
