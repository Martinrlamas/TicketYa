package com.ticketya.ticket.service;

import com.ticketya.ticket.dto.response.EventInvalidationResponse;
import com.ticketya.ticket.dto.response.TicketResponse;
import com.ticketya.ticket.entity.InvalidationReason;
import com.ticketya.ticket.entity.Ticket;
import com.ticketya.ticket.exception.TicketException;
import com.ticketya.ticket.mapper.TicketMapper;
import com.ticketya.ticket.repository.TicketRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(TicketInvalidationService.class);

    private final TicketRepository ticketRepo;
    private final TicketMapper mapper;

    public TicketInvalidationService(TicketRepository ticketRepo, TicketMapper mapper) {
        this.ticketRepo = ticketRepo;
        this.mapper = mapper;
    }

    /**
     * VALID -> REFUNDED | CANCELLED. Idempotente: repetir con el mismo motivo responde 200 sin cambios.
     * USED => 409. La regla vive en {@link Ticket#invalidate}; el lock de fila evita carreras entre
     * dos invalidaciones simultáneas.
     */
    @Transactional
    public TicketResponse invalidate(UUID ticketId, InvalidationReason reason) {
        Ticket ticket = ticketRepo.findByIdForUpdate(ticketId)
                .orElseThrow(() -> TicketException.noEncontrado("Entrada inexistente: " + ticketId));
        if (ticket.invalidate(reason)) {
            log.info("ticket invalidated ticketId={} reason={}", ticketId, reason);
        }
        return mapper.toResponse(ticket);
    }

    /** Cancelación de evento: todas las entradas VALID pasan a CANCELLED en una sola sentencia. */
    @Transactional
    public EventInvalidationResponse invalidarPorEvento(UUID eventId, InvalidationReason reason) {
        if (reason != InvalidationReason.CANCELLED) {
            throw TicketException.validacion("La invalidación masiva por evento solo admite reason=CANCELLED");
        }
        int count = ticketRepo.cancelValidByEvent(eventId);
        log.info("event tickets cancelled eventId={} count={}", eventId, count);
        return new EventInvalidationResponse(eventId, count);
    }
}
