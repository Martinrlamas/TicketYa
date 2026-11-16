package com.ticketya.inventory.repository;

import com.ticketya.inventory.entity.EventInventory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EventInventoryRepository extends JpaRepository<EventInventory, UUID> {

    /** Reclama la inicialización del evento. 0 filas => ya estaba inicializado (o lo está inicializando otra instancia). */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into event_inventory(event_id, status, sold_out_notified, created_at)
            values (:eventId, 'OPEN', false, now())
            on conflict (event_id) do nothing""")
    int insertIfAbsent(UUID eventId);

    /** Reclama el derecho a notificar SOLD_OUT: solo una instancia obtiene 1. */
    @Modifying(flushAutomatically = true)
    @Query("update EventInventory i set i.soldOutNotified = true where i.eventId = :eventId and i.soldOutNotified = false")
    int markSoldOutNotified(UUID eventId);

    /** Reclama el derecho a notificar la reapertura. */
    @Modifying(flushAutomatically = true)
    @Query("update EventInventory i set i.soldOutNotified = false where i.eventId = :eventId and i.soldOutNotified = true")
    int clearSoldOutNotified(UUID eventId);
}
