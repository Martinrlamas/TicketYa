package com.ticketya.order.repository;

import com.ticketya.order.entity.PurchaseQuota;
import com.ticketya.order.entity.PurchaseQuotaId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseQuotaRepository extends JpaRepository<PurchaseQuota, PurchaseQuotaId> {

    /** Crea la fila del contador si no existe (concurrente-seguro). */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into purchase_quota(customer_id, event_id, used)
            values (:customerId, :eventId, 0)
            on conflict (customer_id, event_id) do nothing""")
    int insertIfAbsent(UUID customerId, UUID eventId);

    /**
     * Consumo atómico: suma {@code n} solo si no se supera el límite. 0 filas => PURCHASE_LIMIT_EXCEEDED.
     * Nunca se lee y luego se escribe; el lock de la fila serializa las órdenes simultáneas del mismo cliente.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update PurchaseQuota q set q.used = q.used + :n
             where q.customerId = :customerId and q.eventId = :eventId and q.used + :n <= :limit""")
    int tryConsume(UUID customerId, UUID eventId, int n, int limit);

    @Modifying(flushAutomatically = true)
    @Query("""
            update PurchaseQuota q set q.used = q.used - :n
             where q.customerId = :customerId and q.eventId = :eventId and q.used >= :n""")
    int release(UUID customerId, UUID eventId, int n);
}
