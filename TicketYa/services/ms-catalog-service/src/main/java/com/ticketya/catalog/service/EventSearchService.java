package com.ticketya.catalog.service;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketya.catalog.dto.request.EventSearchCriteria;
import com.ticketya.catalog.dto.response.EventResponse;
import com.ticketya.platform.web.PageResponse;
import com.ticketya.catalog.mapper.EventMapper;
import com.ticketya.catalog.entity.Event;
import com.ticketya.catalog.repository.EventRepository;
import com.ticketya.catalog.repository.EventSpecs;

/** Búsqueda pública: solo PUBLISHED y SOLD_OUT, filtros AND, orden y paginación. */
@Service
public class EventSearchService {

    private final EventRepository repo;
    private final EventMapper mapper;
    private final ZoneId zone;

    public EventSearchService(EventRepository repo, EventMapper mapper, Clock clock) {
        this.repo = repo;
        this.mapper = mapper;
        this.zone = clock.getZone();
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> search(EventSearchCriteria c) {
        boolean relevance = c.sort().equals("relevance") && c.q() != null && !c.q().isBlank();

        Specification<Event> spec = Specification.allOf(
                EventSpecs.publiclyVisible(),
                EventSpecs.type(c.type()),
                EventSpecs.organizer(c.organizerId()),
                EventSpecs.city(c.city()),
                EventSpecs.participant(c.participant()),
                EventSpecs.text(c.q()),
                EventSpecs.from(c.dateFrom(), zone),
                EventSpecs.to(c.dateTo(), zone),
                relevance ? EventSpecs.relevanceOrder(c.q()) : null);

        Pageable pageable = PageRequest.of(c.page(), c.size(), relevance ? Sort.unsorted() : sortFor(c));
        Page<Event> page = repo.findAll(spec, pageable);
        return PageResponse.of(page, mapper::toResponse);
    }

    /** Relevancia sin q equivale a fecha ascendente (spec). */
    private Sort sortFor(EventSearchCriteria c) {
        Sort.Direction dir = Sort.Direction.fromString(c.order());
        Sort tieBreak = Sort.by(Sort.Direction.ASC, "startsAt").and(Sort.by("id"));
        return switch (c.sort()) {
            case "price" -> Sort.by(dir, "minPrice").and(tieBreak);
            case "relevance" -> tieBreak;
            default -> Sort.by(dir, "startsAt").and(Sort.by("id"));
        };
    }
}
