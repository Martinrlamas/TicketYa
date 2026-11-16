package com.ticketya.order.repository;

import com.ticketya.order.entity.OrderStatus;
import com.ticketya.order.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

    Optional<PurchaseOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    /** Bloquea la fila: serializa las transiciones de estado de una orden entre hilos e instancias. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(UUID id);

    /**
     * Órdenes pendientes con la reserva vencida y SIN un pago en curso (esas las resuelve la reconciliación).
     * FOR UPDATE SKIP LOCKED: varias instancias pueden correr el job sin duplicar trabajo.
     */
    @Query(nativeQuery = true, value = """
            select o.* from purchase_order o
             where o.status = 'PENDING_PAYMENT'
               and o.hold_expires_at < :now
               and not exists (select 1 from pay_attempt a
                                where a.order_id = o.id
                                  and a.result in ('PENDING', 'APPROVED', 'COMPENSATING'))
             order by o.hold_expires_at
             limit :batchSize
               for update of o skip locked""")
    List<PurchaseOrder> lockExpiredBatch(Instant now, int batchSize);

    boolean existsByEventIdAndStatus(UUID eventId, OrderStatus status);

    /** Órdenes pendientes de un evento cancelado, sin pago en curso (esas las resuelve la reconciliación). */
    @Query(nativeQuery = true, value = """
            select o.* from purchase_order o
             where o.event_id = :eventId
               and o.status = 'PENDING_PAYMENT'
               and not exists (select 1 from pay_attempt a
                                where a.order_id = o.id
                                  and a.result in ('PENDING', 'APPROVED', 'COMPENSATING'))
             order by o.created_at
             limit :batchSize
               for update of o skip locked""")
    List<PurchaseOrder> lockPendingByEvent(UUID eventId, int batchSize);

    /** Órdenes confirmadas de un evento cancelado que todavía hay que pasar a reintegro. */
    @Query(nativeQuery = true, value = """
            select o.* from purchase_order o
             where o.event_id = :eventId and o.status = 'CONFIRMED'
             order by o.created_at
             limit :batchSize
               for update of o skip locked""")
    List<PurchaseOrder> lockConfirmedByEvent(UUID eventId, int batchSize);
}
