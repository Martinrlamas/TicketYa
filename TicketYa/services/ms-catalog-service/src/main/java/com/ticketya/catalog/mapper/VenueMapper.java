package com.ticketya.catalog.mapper;

import org.springframework.stereotype.Component;

import com.ticketya.catalog.dto.response.RowResponse;
import com.ticketya.catalog.dto.response.SectorResponse;
import com.ticketya.catalog.dto.response.VenueResponse;
import com.ticketya.catalog.entity.Sector;
import com.ticketya.catalog.entity.Venue;

/** Se invoca dentro del service, con la sesión JPA abierta. */
@Component
public class VenueMapper {

    public VenueResponse toResponse(Venue v) {
        return new VenueResponse(v.getId(), v.getName(), v.getAddress(), v.getCity(), v.getLatitude(),
                v.getLongitude(), v.getSectors().stream().map(this::toResponse).toList());
    }

    public SectorResponse toResponse(Sector s) {
        return new SectorResponse(s.getId(), s.getName(), s.getType(), s.capacidadTotal(),
                s.getRows().stream().map(r -> new RowResponse(r.getRowLabel(), r.getSeatCount())).toList());
    }
}
