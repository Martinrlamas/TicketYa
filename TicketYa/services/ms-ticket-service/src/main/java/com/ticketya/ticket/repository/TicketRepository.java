package com.ticketya.ticket.repository;

import com.ticketya.ticket.entity.Ticket;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findByOrderItemIdIn(Collection<UUID> orderItemIds);

    List<Ticket> findByOrderId(UUID orderId);

    /** Serializa invalidaciones concurrentes de una misma entrada. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(UUID id);

    /** Invalidación masiva por cancelación: una sola sentencia, solo toca las VALID (las USED/REFUNDED quedan igual). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Ticket t set t.status = com.ticketya.ticket.entity.TicketStatus.CANCELLED
             where t.eventId = :eventId and t.status = com.ticketya.ticket.entity.TicketStatus.VALID""")
    int cancelValidByEvent(UUID eventId);
}
