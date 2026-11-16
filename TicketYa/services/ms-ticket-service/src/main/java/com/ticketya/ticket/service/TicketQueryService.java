package com.ticketya.ticket.service;

import com.ticketya.platform.web.PageResponse;
import com.ticketya.ticket.client.EventNameResolver;
import com.ticketya.ticket.dto.response.IssueTicketsResponse;
import com.ticketya.ticket.dto.response.TicketResponse;
import com.ticketya.ticket.entity.Ticket;
import com.ticketya.ticket.entity.TicketStatus;
import com.ticketya.ticket.exception.TicketException;
import com.ticketya.ticket.mapper.TicketMapper;
import com.ticketya.ticket.repository.TicketRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketQueryService {

    static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository ticketRepo;
    private final TicketMapper mapper;
    private final EventNameResolver eventName;

    public TicketQueryService(TicketRepository ticketRepo, TicketMapper mapper, EventNameResolver eventName) {
        this.ticketRepo = ticketRepo;
        this.mapper = mapper;
        this.eventName = eventName;
    }

    /** Solo CUSTOMER, y solo ve sus propias entradas (el filtro por customerId sale del header, nunca del request). */
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> listarMios(Identity caller, UUID eventId, TicketStatus status, int page, int size) {
        caller.requireAnyRole(Role.CUSTOMER);
        if (page < 0) {
            throw TicketException.validacion("page debe ser >= 0");
        }
        if (size < 1) {
            throw TicketException.validacion("size debe ser >= 1");
        }
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);

        Specification<Ticket> spec = Specification.where(
                (root, query, cb) -> cb.equal(root.get("customerId"), caller.userId()));
        if (eventId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("eventId"), eventId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        var pageable = PageRequest.of(page, effectiveSize,
                Sort.by(Sort.Direction.DESC, "issuedAt").and(Sort.by("id")));
        //return PageResponse.of(ticketRepo.findAll(spec, pageable), mapper::toResponse);
        Page<Ticket> tickets = ticketRepo.findAll(spec, pageable);
        Map<UUID, String> names = eventName.namesFor(
                tickets.stream().map(Ticket::getEventId).toList());
        return PageResponse.of(tickets, t -> mapper.toResponse(t, names.get(t.getEventId())));
    }

    /**
     * CUSTOMER o ADMIN. Un CUSTOMER que pide la entrada de otro recibe 404 (no se revela que existe).
     */
    @Transactional(readOnly = true)
    public TicketResponse obtenerUno(Identity caller, UUID ticketId) {
        caller.requireAnyRole(Role.CUSTOMER, Role.ADMIN);
        Ticket ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> TicketException.noEncontrado("Entrada inexistente: " + ticketId));
        if (caller.is(Role.CUSTOMER) && !ticket.esDuenio(caller.userId())) {
            throw TicketException.noEncontrado("Entrada inexistente: " + ticketId);
        }
        return mapper.toResponse(ticket, eventName.namesFor(List.of(ticket.getEventId())).get(ticket.getEventId()));
    }

    /** Consulta interna (order-service): todas las entradas de una orden, sin emitir nada. */
    @Transactional(readOnly = true)
    public IssueTicketsResponse listarPorOrden(UUID orderId) {
        return new IssueTicketsResponse(ticketRepo.findByOrderId(orderId).stream().map(mapper::toResponse).toList());
    }
}
