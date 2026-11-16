package com.ticketya.catalog.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketya.catalog.entity.Sector;

public interface SectorRepository extends JpaRepository<Sector, UUID> {

    boolean existsByVenueIdAndNameIgnoreCase(UUID venueId, String name);
}
