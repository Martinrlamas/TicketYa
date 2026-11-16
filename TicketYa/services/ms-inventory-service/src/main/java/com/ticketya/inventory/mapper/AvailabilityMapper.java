package com.ticketya.inventory.mapper;

import com.ticketya.inventory.dto.response.AvailabilityResponse.SeatAvailability;
import com.ticketya.inventory.dto.response.AvailabilityResponse.SectorAvailability;
import com.ticketya.inventory.entity.EventSeat;
import com.ticketya.inventory.entity.EventSector;
import com.ticketya.inventory.entity.SeatStatus;
import com.ticketya.inventory.entity.ZoneStock;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityMapper {

    public SectorAvailability numbered(EventSector sector, List<EventSeat> seats) {
        List<SeatAvailability> dtos = seats.stream()
                .map(s -> new SeatAvailability(s.getId(), s.getRowLabel(), s.getSeatNumber(), s.getStatus()))
                .toList();
        int available = (int) seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        return new SectorAvailability(sector.getSectorId(), sector.getName(), sector.getType(),
                seats.size(), available, dtos);
    }

    public SectorAvailability general(EventSector sector, ZoneStock zone) {
        return new SectorAvailability(sector.getSectorId(), sector.getName(), sector.getType(),
                zone.getCapacity(), zone.available(), null);
    }
}
