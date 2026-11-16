package com.ticketya.order.client.rest;

import com.ticketya.order.client.TicketPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TicketRestAdapter implements TicketPort {

    record IssueWire(UUID orderId, UUID eventId, UUID customerId, List<ItemWire> items) {}

    record ItemWire(UUID orderItemId, UUID sectorId, UUID seatId, String seatLabel) {}

    record IssueResponseWire(List<TicketWire> tickets) {}

    record TicketWire(UUID ticketId, UUID orderItemId, UUID eventId, UUID sectorId, UUID seatId, String seatLabel,
                      String qrCode, String status, OffsetDateTime issuedAt) {}

    record InvalidateWire(String reason) {}

    record EventInvalidationWire(UUID eventId, int invalidated) {}

    private final RestClient client;

    public TicketRestAdapter(@Qualifier("ticketRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public List<TicketInfo> issue(UUID orderId, UUID eventId, UUID customerId, List<TicketItem> items) {
        var body = new IssueWire(orderId, eventId, customerId, items.stream()
                .map(i -> new ItemWire(i.orderItemId(), i.sectorId(), i.seatId(), i.seatLabel())).toList());
        IssueResponseWire res = UpstreamErrors.call("ticket-service", () -> client.post()
                .uri("/internal/tickets")
                .body(body)
                .retrieve()
                .body(IssueResponseWire.class));
        return res.tickets().stream()
                .map(t -> new TicketInfo(t.ticketId(), t.orderItemId(), t.eventId(), t.sectorId(), t.seatId(),
                        t.seatLabel(), t.qrCode(), t.status(), t.issuedAt().toInstant()))
                .toList();
    }

    @Override
    public TicketInfo invalidate(UUID ticketId, String reason) {
        TicketWire t = UpstreamErrors.call("ticket-service", () -> client.post()
                .uri("/internal/tickets/{id}/invalidate", ticketId)
                .body(new InvalidateWire(reason))
                .retrieve()
                .body(TicketWire.class));
        return new TicketInfo(t.ticketId(), t.orderItemId(), t.eventId(), t.sectorId(), t.seatId(), t.seatLabel(),
                t.qrCode(), t.status(), t.issuedAt().toInstant());
    }

    @Override
    public int invalidateEvent(UUID eventId, String reason) {
        EventInvalidationWire res = UpstreamErrors.call("ticket-service", () -> client.post()
                .uri("/internal/events/{id}/tickets/invalidate", eventId)
                .body(new InvalidateWire(reason))
                .retrieve()
                .body(EventInvalidationWire.class));
        return res.invalidated();
    }
}
