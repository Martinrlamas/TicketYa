package com.ticketya.catalog.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketya.catalog.entity.Event;
import com.ticketya.catalog.entity.EventStatus;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    Page<Event> findByOrganizerIdAndStatus(UUID organizerId, EventStatus status, Pageable pageable);

    /** UPDATE masivo y condicional: idempotente, varias instancias pueden correrlo a la vez. */
    @Modifying
    @Query("update Event e set e.status = :finished, e.version = e.version + 1 "
            + "where e.status in :active and e.startsAt < :now")
    int finishPastEvents(@Param("finished") EventStatus finished,
                         @Param("active") Collection<EventStatus> active,
                         @Param("now") OffsetDateTime now);
}
