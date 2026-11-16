package com.ticketya.catalog.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketya.catalog.dto.request.CreateSectorRequest;
import com.ticketya.catalog.dto.request.CreateVenueRequest;
import com.ticketya.catalog.dto.request.RowRequest;
import com.ticketya.catalog.dto.response.SectorResponse;
import com.ticketya.catalog.dto.response.VenueResponse;
import com.ticketya.catalog.mapper.VenueMapper;
import com.ticketya.catalog.entity.Sector;
import com.ticketya.catalog.entity.SectorRow;
import com.ticketya.catalog.entity.SectorType;
import com.ticketya.catalog.entity.Venue;
import com.ticketya.catalog.repository.SectorRepository;
import com.ticketya.catalog.repository.VenueRepository;
import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;

@Service
public class VenueService {

    private final VenueRepository venues;
    private final SectorRepository sectors;
    private final VenueMapper mapper;

    public VenueService(VenueRepository venues, SectorRepository sectors, VenueMapper mapper) {
        this.venues = venues;
        this.sectors = sectors;
        this.mapper = mapper;
    }

    @Transactional
    public VenueResponse create(CreateVenueRequest req) {
        Venue venue = venues.save(Venue.create(req.name(), req.address(), req.city(), req.latitude(), req.longitude()));
        return mapper.toResponse(venue);
    }

    @Transactional(readOnly = true)
    public VenueResponse get(UUID venueId) {
        return mapper.toResponse(venues.findById(venueId).orElseThrow(() -> noEncontrado(venueId)));
    }

    @Transactional
    public SectorResponse agregarSector(UUID venueId, CreateSectorRequest req) {
        Venue venue = venues.findById(venueId).orElseThrow(() -> noEncontrado(venueId));
        if (sectors.existsByVenueIdAndNameIgnoreCase(venueId, req.name().trim())) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                    "Ya existe un sector con el nombre '" + req.name().trim() + "' en este lugar");
        }
        boolean hasRows = req.rows() != null && !req.rows().isEmpty();
        Sector sector;
        if (req.type() == SectorType.NUMBERED) {
            if (req.capacity() != null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                        "Un sector numerado no admite capacity: se deriva de sus filas");
            }
            List<SectorRow> rows = hasRows
                    ? req.rows().stream().map(this::toRow).toList()
                    : List.of();
            sector = Sector.numbered(venue, req.name().trim(), rows);
        } else {
            if (hasRows) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Un sector general no admite filas");
            }
            sector = Sector.general(venue, req.name().trim(), req.capacity());
        }
        return mapper.toResponse(sectors.save(sector));
    }

    private SectorRow toRow(RowRequest r) {
        return new SectorRow(r.rowLabel(), r.seatCount());
    }

    private PlatformException noEncontrado(UUID venueId) {
        return new PlatformException(ErrorCode.NOT_FOUND, "No existe el lugar " + venueId);
    }
}
