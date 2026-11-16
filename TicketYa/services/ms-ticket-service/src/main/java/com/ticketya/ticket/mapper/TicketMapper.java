package com.ticketya.ticket.mapper;

import com.ticketya.ticket.dto.response.TicketResponse;
import com.ticketya.ticket.entity.Ticket;
import org.springframework.stereotype.Component;

@Component
public class TicketMapper {

    public TicketResponse toResponse(Ticket t) {
        return toResponse(t, null);          // consultas internas: sin nombre de evento
    }
    public TicketResponse toResponse(Ticket t, String eventName) {
        return new TicketResponse(t.getId(), t.getOrderItemId(), t.getEventId(),eventName, t.getSectorId(), t.getSeatId(),
                t.getSeatLabel(), t.getQrCode(), t.getStatus(), t.getIssuedAt());
    }
}
