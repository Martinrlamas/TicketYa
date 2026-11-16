package com.ticketya.inventory.service;

import com.ticketya.inventory.config.InventoryProperties;
import com.ticketya.inventory.dto.request.CreateHoldRequest;
import com.ticketya.inventory.dto.request.StockItemRequest;
import com.ticketya.inventory.entity.EventInventory;
import com.ticketya.inventory.entity.EventSeat;
import com.ticketya.inventory.entity.EventSector;
import com.ticketya.inventory.entity.Hold;
import com.ticketya.inventory.entity.HoldItem;
import com.ticketya.inventory.entity.HoldStatus;
import com.ticketya.inventory.entity.SeatStatus;
import com.ticketya.inventory.entity.ZoneStockId;
import com.ticketya.inventory.event.StockChanged;
import com.ticketya.inventory.exception.HoldExpiredException;
import com.ticketya.inventory.exception.InventoryException;
import com.ticketya.inventory.repository.EventInventoryRepository;
import com.ticketya.inventory.repository.EventSeatRepository;
import com.ticketya.inventory.repository.HoldItemRepository;
import com.ticketya.inventory.repository.HoldRepository;
import com.ticketya.inventory.repository.ZoneStockRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de holds. La garantía de no doble venta NO está en este código sino en las sentencias
 * condicionales de los repositories: aquí solo se orquestan, se ordenan los IDs (evita deadlocks)
 * y se traduce "0 filas afectadas" a errores de negocio.
 */
@Service
public class HoldService {

    public record HoldResult(Hold hold, boolean created, List<EventSeat> seats) {}

    private final EventInventoryRepository inventoryRepo;
    private final EventSeatRepository seatRepo;
    private final ZoneStockRepository zoneRepo;
    private final HoldRepository holdRepo;
    private final HoldItemRepository itemRepo;
    private final SectorResolver sectorResolver;
    private final HoldStockOperations stockOps;
    private final ApplicationEventPublisher events;
    private final InventoryProperties props;
    private final Clock clock;

    public HoldService(EventInventoryRepository inventoryRepo, EventSeatRepository seatRepo,
                       ZoneStockRepository zoneRepo, HoldRepository holdRepo, HoldItemRepository itemRepo,
                       SectorResolver sectorResolver, HoldStockOperations stockOps,
                       ApplicationEventPublisher events, InventoryProperties props, Clock clock) {
        this.inventoryRepo = inventoryRepo;
        this.seatRepo = seatRepo;
        this.zoneRepo = zoneRepo;
        this.holdRepo = holdRepo;
        this.itemRepo = itemRepo;
        this.sectorResolver = sectorResolver;
        this.stockOps = stockOps;
        this.events = events;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Todo-o-nada: cualquier excepción de runtime revierte la transacción completa
     * (incluido el INSERT del hold), así que nunca queda un pedido a medias.
     */
    @Transactional
    public HoldResult create(CreateHoldRequest req) {
        EventInventory inventory = inventoryRepo.findById(req.eventId())
                .orElseThrow(() -> InventoryException.noEncontrado("El evento " + req.eventId() + " no tiene inventario"));

        // Idempotencia: mismo orderId => mismo hold (se evalúa antes que "cerrado": un reintento no debe fallar)
        Optional<Hold> existing = holdRepo.findByOrderId(req.orderId());
        if (existing.isPresent()) {
            return result(existing.get(), false);
        }
        if (inventory.isClosed()) {
            throw InventoryException.eventoNoComprable("El evento " + req.eventId() + " está cerrado");
        }

        Duration ttl = req.ttlSeconds() != null ? Duration.ofSeconds(req.ttlSeconds()) : props.holdTtl();
        UUID holdId = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(ttl);

        // Carrera entre dos requests con el mismo orderId: el UNIQUE decide y el perdedor devuelve el existente.
        if (holdRepo.insertIfAbsent(holdId, req.eventId(), req.orderId(), expiresAt) == 0) {
            return result(holdRepo.findByOrderId(req.orderId()).orElseThrow(), false);
        }

        Map<UUID, EventSector> sectors = sectorResolver.forEvent(req.eventId());
        List<StockItemRequest> items = req.items().stream()
                .sorted(Comparator.comparing(StockItemRequest::sectorId))   // orden estable entre compradores
                .toList();
        for (StockItemRequest item : items) {
            sectorResolver.require(sectors, item);
            if (item.isNumbered()) {
                reservarAsientos(holdId, req.eventId(), item);
            } else {
                reservarZona(holdId, req.eventId(), item);
            }
        }

        events.publishEvent(new StockChanged(req.eventId()));
        return result(holdRepo.findById(holdId).orElseThrow(), true);
    }

    /** Devuelve el hold con sus asientos (etiquetas), para que order arme el snapshot sin consultar la disponibilidad. */
    private HoldResult result(Hold hold, boolean created) {
        return new HoldResult(hold, created, seatRepo.findByHoldIdOrderByRowLabelAscSeatNumberAsc(hold.getId()));
    }

    private void reservarAsientos(UUID holdId, UUID eventId, StockItemRequest item) {
        List<UUID> ids = item.seatIds().stream().distinct().sorted().toList();   // orden fijo => sin deadlocks
        int updated = seatRepo.markHeld(holdId, eventId, item.sectorId(), ids);
        if (updated != ids.size()) {
            throw seatFailure(holdId, eventId, item.sectorId(), ids);
        }
        itemRepo.saveAll(ids.stream().map(id -> HoldItem.seat(holdId, item.sectorId(), id)).toList());
    }

    private InventoryException seatFailure(UUID holdId, UUID eventId, UUID sectorId, List<UUID> ids) {
        List<EventSeat> found = seatRepo.findByEventIdAndSectorIdAndIdIn(eventId, sectorId, ids);
        if (found.size() < ids.size()) {
            return InventoryException.noEncontrado("Algún asiento no existe en el sector " + sectorId);
        }
        String taken = found.stream()
                .filter(s -> !holdId.equals(s.getHoldId()))        // los que tomó este mismo UPDATE no cuentan
                .filter(s -> s.getStatus() != SeatStatus.AVAILABLE)
                .map(EventSeat::label)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("desconocido");
        return InventoryException.asientoNoDisponible("No disponible(s): " + taken);
    }

    private void reservarZona(UUID holdId, UUID eventId, StockItemRequest item) {
        if (zoneRepo.tryHold(eventId, item.sectorId(), item.quantity()) == 0) {
            if (!zoneRepo.existsById(new ZoneStockId(eventId, item.sectorId()))) {
                throw InventoryException.noEncontrado("La zona " + item.sectorId() + " no tiene stock");
            }
            throw InventoryException.capacidadInsuficiente(
                    "No hay " + item.quantity() + " lugares libres en la zona " + item.sectorId());
        }
        itemRepo.save(HoldItem.zone(holdId, item.sectorId(), item.quantity()));
    }

    /**
     * {@code noRollbackFor}: al vencer, se libera el stock y se lanza 410. Sin esto el rollback
     * desharía la liberación y el hold quedaría ACTIVE hasta que lo levante el job.
     */
    @Transactional(noRollbackFor = HoldExpiredException.class)
    public Hold confirm(UUID holdId) {
        Hold hold = holdRepo.findByIdForUpdate(holdId)
                .orElseThrow(() -> InventoryException.noEncontrado("Hold inexistente: " + holdId));

        switch (hold.getStatus()) {
            case CONFIRMED -> { return hold; }                                         // idempotente
            case EXPIRED -> throw new HoldExpiredException(hold.getExpiresAt());
            case RELEASED -> throw InventoryException.estadoInvalido("El hold " + holdId + " fue liberado");
            case ACTIVE -> { /* continúa */ }
        }

        if (hold.isExpired(clock.instant())) {                                          // expiración perezosa
            stockOps.release(hold);
            hold.expire();
            events.publishEvent(new StockChanged(hold.getEventId()));
            throw new HoldExpiredException(hold.getExpiresAt());
        }

        stockOps.confirm(hold);
        hold.confirm();
        events.publishEvent(new StockChanged(hold.getEventId()));
        return hold;
    }

    /** Idempotente: un hold que no está ACTIVE no se toca. */
    @Transactional
    public void release(UUID holdId) {
        Hold hold = holdRepo.findByIdForUpdate(holdId)
                .orElseThrow(() -> InventoryException.noEncontrado("Hold inexistente: " + holdId));
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            return;
        }
        stockOps.release(hold);
        hold.release();
        events.publishEvent(new StockChanged(hold.getEventId()));
    }
}
