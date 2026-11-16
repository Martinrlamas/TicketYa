package com.ticketya.catalog.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketya.catalog.entity.Venue;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
