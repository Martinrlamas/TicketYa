package com.ticketya.order.repository;

import com.ticketya.order.entity.EventCancellation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EventCancellationRepository extends JpaRepository<EventCancellation, UUID> {

    /** Idempotente y seguro ante concurrencia: devuelve 1 solo para quien la registra primero. */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into event_cancellation (event_id, reason, orders_expired, items_to_refund, inventory_closed,
                                            tickets_invalidated, settled, created_at, updated_at)
            values (:eventId, :reason, 0, 0, false, false, false, :now, :now)
            on conflict (event_id) do nothing""")
    int insertIfAbsent(UUID eventId, String reason, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EventCancellation c where c.eventId = :eventId")
    Optional<EventCancellation> findByIdForUpdate(UUID eventId);

    List<EventCancellation> findBySettledFalse();
}
