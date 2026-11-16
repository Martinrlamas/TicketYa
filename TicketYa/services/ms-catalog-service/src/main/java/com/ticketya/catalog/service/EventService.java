package com.ticketya.catalog.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketya.catalog.client.InventoryClient;
import com.ticketya.catalog.client.OrderClient;
import com.ticketya.catalog.client.dto.InventoryRowDto;
import com.ticketya.catalog.client.dto.InventorySectorDto;
import com.ticketya.catalog.dto.request.CreateEventRequest;
import com.ticketya.catalog.dto.request.ParticipantRequest;
import com.ticketya.catalog.dto.request.SectorPricesRequest;
import com.ticketya.catalog.dto.request.UpdateEventRequest;
import com.ticketya.catalog.dto.response.EventResponse;
import com.ticketya.catalog.dto.response.InternalStatusResponse;
import com.ticketya.platform.web.PageResponse;
import com.ticketya.catalog.dto.response.SaleInfoResponse;
import com.ticketya.catalog.mapper.EventMapper;
import com.ticketya.catalog.entity.Event;
import com.ticketya.catalog.entity.EventSectorPrice;
import com.ticketya.catalog.entity.EventStatus;
import com.ticketya.catalog.entity.Sector;
import com.ticketya.catalog.entity.SectorType;
import com.ticketya.catalog.entity.Venue;
import com.ticketya.catalog.repository.EventRepository;
import com.ticketya.catalog.repository.SectorRepository;
import com.ticketya.catalog.repository.VenueRepository;
import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;

@Service
public class EventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EventRepository events;
    private final VenueRepository venues;
    private final SectorRepository sectors;
    private final InventoryClient inventory;
    private final OrderClient orders;
    private final EventMapper mapper;
    private final Clock clock;

    public EventService(EventRepository events, VenueRepository venues, SectorRepository sectors,
            InventoryClient inventory, OrderClient orders, EventMapper mapper, Clock clock) {
        this.events = events;
        this.venues = venues;
        this.sectors = sectors;
        this.inventory = inventory;
        this.orders = orders;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ organizador

    @Transactional
    public EventResponse create(Identity user, CreateEventRequest req) {
        Venue venue = venues.findById(req.venueId()).orElseThrow(() -> noEncontrado("Lugar"));
        Event e = Event.draft(user.userId(), venue, req.name(), req.description(), req.type(), req.startsAt(), clock);
        e.reemplazarParticipantes(toParticipantData(req.participants()));
        return mapper.toResponse(events.save(e));
    }

    @Transactional
    public EventResponse update(Identity user, UUID eventId, UpdateEventRequest req) {
        Event e = cargarPropio(eventId, user);
        Venue venue = venues.findById(req.venueId()).orElseThrow(() -> noEncontrado("Lugar"));
        e.update(venue, req.name(), req.description(), req.type(), req.startsAt(), clock);
        e.reemplazarParticipantes(toParticipantData(req.participants()));
        return mapper.toResponse(e); // dirty checking: no hace falta save()
    }

    @Transactional
    public EventResponse setPrices(Identity user, UUID eventId, SectorPricesRequest req) {
        Event e = cargarPropio(eventId, user);
        Set<UUID> seen = new HashSet<>();
        for (var item : req.prices()) {
            if (!seen.add(item.sectorId())) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "El sector " + item.sectorId() + " está repetido");
            }
            if (!Event.CURRENCY.equals(item.currency())) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Solo se admite la moneda " + Event.CURRENCY);
            }
        }
        Map<UUID, Sector> found = new LinkedHashMap<>();
        sectors.findAllById(seen).forEach(s -> found.put(s.getId(), s));
        Map<Sector, BigDecimal> prices = new LinkedHashMap<>();
        for (var item : req.prices()) {
            Sector sector = found.get(item.sectorId());
            if (sector == null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "No existe el sector " + item.sectorId());
            }
            prices.put(sector, item.price());
        }
        e.reemplazarPrecios(prices); // las reglas las valida la entidad
        return mapper.toResponse(e);
    }

    /** Valida -> efecto externo idempotente (si falla: 503 y sigue DRAFT) -> cambia el estado. */
    @Transactional
    public EventResponse publish(Identity user, UUID eventId) {
        Event e = cargarPropio(eventId, user);
        e.assertPublishable(clock);
        inventory.initialize(e.getId(), toInventoryDto(e));
        e.marcarPublicado();
        return mapper.toResponse(e);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listarMios(Identity user, EventStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                    "page debe ser >= 0 y size debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Page<Event> result = status == null
                ? events.findByOrganizerId(user.userId(), pageable)
                : events.findByOrganizerIdAndStatus(user.userId(), status, pageable);
        return PageResponse.of(result, mapper::toResponse);
    }

    // ------------------------------------------------------------------ consulta pública

    @Transactional(readOnly = true)
    public EventResponse obtenerVisible(Optional<Identity> user, UUID id) {
        Event e = events.findById(id).orElseThrow(() -> noEncontrado("Evento"));
        boolean canSee = e.esVisiblePublicamente()
                || user.map(u -> u.is(Role.ADMIN) || e.esDuenio(u.userId())).orElse(false);
        if (!canSee) {
            throw noEncontrado("Evento"); // 404, no 403: no revelar que existe
        }
        return mapper.toResponse(e);
    }

    // ------------------------------------------------------------------ ADMIN

    @Transactional
    public EventResponse suspend(UUID eventId) {
        Event e = load(eventId);
        e.suspend();
        return mapper.toResponse(e);
    }

    @Transactional
    public EventResponse resume(UUID eventId) {
        Event e = load(eventId);
        e.resume();
        return mapper.toResponse(e);
    }

    /**
     * Cancela y notifica. Si inventory u order fallan se lanza 503 y la transacción revierte (el evento queda
     * como estaba); ambos llamados son idempotentes, así que el ADMIN puede reintentar sin riesgo.
     */
    @Transactional
    public EventResponse cancel(UUID eventId, String reason) {
        Event e = load(eventId);
        boolean wasOnSale = e.getStatus() != EventStatus.DRAFT; // un borrador nunca tuvo inventario ni órdenes
        e.cancel();
        events.saveAndFlush(e);
        if (wasOnSale) {
            closeInventory(eventId);
            orders.notifyEventCancelled(eventId, reason);
        }
        return mapper.toResponse(e);
    }

    // ------------------------------------------------------------------ interno

    /** Para order-service: si es comprable y qué sectores/precios tiene. */
    @Transactional(readOnly = true)
    public SaleInfoResponse saleInfo(UUID eventId) {
        Event e = load(eventId);
        List<SaleInfoResponse.SaleSector> sectorsInfo = e.getPrices().stream()
                .map((EventSectorPrice p) -> new SaleInfoResponse.SaleSector(p.getSector().getId(),
                        p.getSector().getName(), p.getSector().getType(), p.getPrice(), p.getCurrency()))
                .toList();
        return new SaleInfoResponse(e.getId(), e.getStatus(), e.esComprable(clock), e.getStartsAt(),
                e.getOrganizerId(), sectorsInfo);
    }

    /** Para otros servicios (ej. ticket-service): el evento sin filtro de visibilidad, incluso cancelado. */
    @Transactional(readOnly = true)
    public EventResponse getAnyStatus(UUID id) {
        return mapper.toResponse(load(id));
    }

    /** Para inventory: SOLD_OUT <-> PUBLISHED. */
    @Transactional
    public InternalStatusResponse applyInventoryStatus(UUID eventId, EventStatus status) {
        Event e = load(eventId);
        e.applyInventoryStatus(status);
        return new InternalStatusResponse(e.getId(), e.getStatus());
    }

    // ------------------------------------------------------------------ helpers

    private void closeInventory(UUID eventId) {
        try {
            inventory.close(eventId);
        } catch (PlatformException ex) {
            if (ex.code() != ErrorCode.NOT_FOUND) { // sin inventario no hay nada que cerrar
                throw ex;
            }
        }
    }

    private List<InventorySectorDto> toInventoryDto(Event e) {
        return e.getPrices().stream().map(p -> {
            Sector s = p.getSector();
            if (s.getType() == SectorType.NUMBERED) {
                List<InventoryRowDto> rows = s.getRows().stream()
                        .map(r -> new InventoryRowDto(r.getRowLabel(), r.getSeatCount())).toList();
                return new InventorySectorDto(s.getId(), SectorType.NUMBERED.name(), rows, null);
            }
            return new InventorySectorDto(s.getId(), SectorType.GENERAL.name(), null, s.getCapacity());
        }).toList();
    }

    private List<Event.ParticipantData> toParticipantData(List<ParticipantRequest> participants) {
        return participants == null ? List.of()
                : participants.stream().map(p -> new Event.ParticipantData(p.name(), p.role())).toList();
    }

    private Event load(UUID id) {
        return events.findById(id).orElseThrow(() -> noEncontrado("Evento"));
    }

    private Event cargarPropio(UUID id, Identity user) {
        Event e = load(id);
        if (!e.esDuenio(user.userId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "El evento es de otro organizador");
        }
        return e;
    }

    private PlatformException noEncontrado(String what) {
        return new PlatformException(ErrorCode.NOT_FOUND, what + " inexistente");
    }
}
