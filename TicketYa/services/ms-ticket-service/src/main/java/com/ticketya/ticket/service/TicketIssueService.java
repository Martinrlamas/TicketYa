package com.ticketya.ticket.service;

import com.ticketya.ticket.dto.request.IssueTicketsRequest;
import com.ticketya.ticket.dto.response.IssueTicketsResponse;
import com.ticketya.ticket.dto.response.TicketResponse;
import com.ticketya.ticket.entity.Ticket;
import com.ticketya.ticket.exception.TicketException;
import com.ticketya.ticket.mapper.TicketMapper;
import com.ticketya.ticket.repository.TicketIssueRepository;
import com.ticketya.ticket.repository.TicketIssueRepository.NewTicket;
import com.ticketya.ticket.repository.TicketRepository;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emisión idempotente por orderItemId. */
@Service
public class TicketIssueService {

    public record IssueResult(IssueTicketsResponse body, boolean created) {}

    private static final Logger log = LoggerFactory.getLogger(TicketIssueService.class);

    private final TicketIssueRepository issueRepo;
    private final TicketRepository ticketRepo;
    private final QrTokenGenerator qr;
    private final TicketMapper mapper;
    private final Clock clock;

    public TicketIssueService(TicketIssueRepository issueRepo, TicketRepository ticketRepo, QrTokenGenerator qr,
                              TicketMapper mapper, Clock clock) {
        this.issueRepo = issueRepo;
        this.ticketRepo = ticketRepo;
        this.qr = qr;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Una entrada por ítem. Cada ítem se inserta con ON CONFLICT DO NOTHING: si ya existía (reintento) o lo
     * está insertando otra solicitud en este instante, la base decide y aquí solo se devuelve lo que quedó.
     * Los ítems se procesan ordenados por orderItemId para que dos solicitudes solapadas no se bloqueen entre sí.
     *
     * @return created=true si se creó al menos una entrada (HTTP 201); false si todas ya existían (HTTP 200)
     */
    @Transactional
    public IssueResult issue(IssueTicketsRequest req) {
        Set<UUID> seen = new HashSet<>();
        for (IssueTicketsRequest.Item item : req.items()) {
            if (!seen.add(item.orderItemId())) {
                throw TicketException.validacion("orderItemId repetido: " + item.orderItemId());
            }
        }

        int inserted = 0;
        List<IssueTicketsRequest.Item> ordered = req.items().stream()
                .sorted(Comparator.comparing(IssueTicketsRequest.Item::orderItemId)).toList();
        for (IssueTicketsRequest.Item item : ordered) {
            inserted += issueRepo.insertIfAbsent(new NewTicket(
                    req.orderId(), item.orderItemId(), req.eventId(), req.customerId(), item.sectorId(),
                    item.seatId(), item.seatLabel(), qr.generate(), clock.instant()));
        }

        Map<UUID, Ticket> byItem = ticketRepo.findByOrderItemIdIn(seen).stream()
                .collect(Collectors.toMap(Ticket::getOrderItemId, Function.identity()));
        List<TicketResponse> tickets = req.items().stream()          // misma orden que el request
                .map(i -> mapper.toResponse(byItem.get(i.orderItemId())))
                .toList();

        log.info("tickets issued orderId={} new={} total={}", req.orderId(), inserted, tickets.size());
        return new IssueResult(new IssueTicketsResponse(tickets), inserted > 0);
    }
}
