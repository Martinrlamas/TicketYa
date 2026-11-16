package com.ticketya.inventory.repository;

import com.ticketya.inventory.entity.Hold;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HoldRepository extends JpaRepository<Hold, UUID> {

    Optional<Hold> findByOrderId(UUID orderId);

    /** Serializa confirm / release / job de expiración sobre el mismo hold. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.id = :id")
    Optional<Hold> findByIdForUpdate(UUID id);

    /** Idempotencia por orderId (UNIQUE): 0 filas => ya existe un hold para esa orden. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into hold(id, event_id, order_id, status, expires_at, created_at)
            values (:id, :eventId, :orderId, 'ACTIVE', :expiresAt, now())
            on conflict (order_id) do nothing""")
    int insertIfAbsent(UUID id, UUID eventId, UUID orderId, Instant expiresAt);

    /** Holds vencidos bloqueados con SKIP LOCKED: varias instancias pueden correr el job sin duplicar trabajo. */
    @Query(nativeQuery = true, value = """
            select * from hold
             where status = 'ACTIVE' and expires_at < :now
             order by expires_at
             limit :batchSize
               for update skip locked""")
    List<Hold> lockExpiredBatch(Instant now, int batchSize);
}
