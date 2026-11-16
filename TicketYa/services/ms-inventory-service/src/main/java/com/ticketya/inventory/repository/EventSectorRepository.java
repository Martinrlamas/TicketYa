package com.ticketya.inventory.repository;

import com.ticketya.inventory.entity.EventSector;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EventSectorRepository extends JpaRepository<EventSector, EventSector.Key> {

    List<EventSector> findByEventIdOrderByPositionAsc(UUID eventId);
}
