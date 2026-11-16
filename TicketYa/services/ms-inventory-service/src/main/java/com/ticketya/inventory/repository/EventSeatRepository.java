package com.ticketya.inventory.repository;

import com.ticketya.inventory.entity.EventSeat;
import com.ticketya.inventory.entity.SeatStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Todas las transiciones de estado son UPDATE condicionales: la base decide quién gana.
 * Nunca se lee-y-luego-escribe. Los IDs llegan ordenados desde el service (evita deadlocks).
 */
public interface EventSeatRepository extends JpaRepository<EventSeat, UUID> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update EventSeat s set s.status = com.ticketya.inventory.entity.SeatStatus.HELD, s.holdId = :holdId, s.version = s.version + 1
             where s.id in :ids and s.eventId = :eventId and s.sectorId = :sectorId
               and s.status = com.ticketya.inventory.entity.SeatStatus.AVAILABLE""")
    int markHeld(UUID holdId, UUID eventId, UUID sectorId, Collection<UUID> ids);

    @Modifying(flushAutomatically = true)
    @Query("""
            update EventSeat s set s.status = com.ticketya.inventory.entity.SeatStatus.SOLD, s.version = s.version + 1
             where s.holdId = :holdId and s.status = com.ticketya.inventory.entity.SeatStatus.HELD""")
    int markSold(UUID holdId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update EventSeat s set s.status = com.ticketya.inventory.entity.SeatStatus.AVAILABLE, s.holdId = null, s.version = s.version + 1
             where s.holdId = :holdId and s.status = com.ticketya.inventory.entity.SeatStatus.HELD""")
    int releaseHeld(UUID holdId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update EventSeat s set s.status = com.ticketya.inventory.entity.SeatStatus.AVAILABLE, s.holdId = null, s.version = s.version + 1
             where s.id in :ids and s.eventId = :eventId and s.sectorId = :sectorId
               and s.status = com.ticketya.inventory.entity.SeatStatus.SOLD""")
    int releaseSold(UUID eventId, UUID sectorId, Collection<UUID> ids);

    List<EventSeat> findByEventIdAndSectorIdAndIdIn(UUID eventId, UUID sectorId, Collection<UUID> ids);

    List<EventSeat> findByEventIdOrderBySectorIdAscRowLabelAscSeatNumberAsc(UUID eventId);

    /** Asientos de un hold (usa ix_event_seat_hold). */
    List<EventSeat> findByHoldIdOrderByRowLabelAscSeatNumberAsc(UUID holdId);

    boolean existsByEventIdAndStatus(UUID eventId, SeatStatus status);
}
