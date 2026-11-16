package com.ticketya.catalog.mapper;

import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.ticketya.catalog.dto.response.EventResponse;
import com.ticketya.catalog.dto.response.ParticipantResponse;
import com.ticketya.catalog.dto.response.PriceResponse;
import com.ticketya.catalog.entity.Event;

@Component
public class EventMapper {

    public EventResponse toResponse(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getDescription(), e.getType(), e.getOrganizerId(),
                e.getVenue().getId(), e.getVenue().getCity(), e.getStartsAt(), e.getStatus(),
                e.getParticipants().stream().map(p -> new ParticipantResponse(p.getName(), p.getRole())).toList(),
                e.getPrices().stream()
                        .map(p -> new PriceResponse(p.getSector().getId(), p.getSector().getName(), p.getPrice(),
                                p.getCurrency()))
                        .sorted(Comparator.comparing(PriceResponse::sectorName))
                        .toList(),
                e.getMinPrice(), e.getCreatedAt());
    }
}
