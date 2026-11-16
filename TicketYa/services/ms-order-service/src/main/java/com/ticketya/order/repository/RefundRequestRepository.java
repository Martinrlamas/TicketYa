package com.ticketya.order.repository;

import com.ticketya.order.entity.RefundRequest;
import com.ticketya.order.entity.RefundRequestStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundRequest r where r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(UUID id);

    /** Solo el orderId (sin cargar la entidad): permite bloquear primero la orden y después la solicitud. */
    @Query("select r.orderId from RefundRequest r where r.id = :id")
    Optional<UUID> findOrderId(UUID id);

    List<RefundRequest> findByOrderItemIdInAndStatusIn(Collection<UUID> orderItemIds, Collection<RefundRequestStatus> statuses);

    Page<RefundRequest> findByCustomerId(UUID customerId, Pageable pageable);

    Page<RefundRequest> findByStatus(RefundRequestStatus status, Pageable pageable);

    /**
     * Solicitudes PROCESSING listas para reintentar. SKIP LOCKED: varias instancias del job no se pisan.
     * El llamador les pone un lease (next_attempt_at) dentro de la misma transacción.
     */
    @Query(nativeQuery = true, value = """
            select r.* from refund_request r
             where r.status = 'PROCESSING' and r.next_attempt_at <= :now
             order by r.created_at
             limit :batchSize
               for update skip locked""")
    List<RefundRequest> lockDue(Instant now, int batchSize);
}
