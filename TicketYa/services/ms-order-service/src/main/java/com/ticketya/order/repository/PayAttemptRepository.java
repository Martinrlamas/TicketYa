package com.ticketya.order.repository;

import com.ticketya.order.entity.PayAttempt;
import com.ticketya.order.entity.PayAttemptResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PayAttemptRepository extends JpaRepository<PayAttempt, UUID> {

    Optional<PayAttempt> findByIdempotencyKey(String idempotencyKey);

    List<PayAttempt> findByOrderIdAndResultIn(UUID orderId, Collection<PayAttemptResult> results);

    /** Órdenes con un pago en curso sin novedades desde {@code before}: candidatas a reconciliar. */
    @Query(nativeQuery = true, value = """
            select distinct a.order_id from pay_attempt a
             where a.result in ('PENDING', 'APPROVED', 'COMPENSATING') and a.updated_at < :before""")
    List<UUID> findStaleInFlightOrderIds(Instant before);
}
