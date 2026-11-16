package com.ticketya.inventory.repository;

import com.ticketya.inventory.entity.ZoneStock;
import com.ticketya.inventory.entity.ZoneStockId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ZoneStockRepository extends JpaRepository<ZoneStock, ZoneStockId> {

    /** Una sola sentencia: 0 filas => capacidad insuficiente. El CHECK de la tabla es la red de seguridad. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ZoneStock z set z.held = z.held + :n, z.version = z.version + 1
             where z.eventId = :eventId and z.sectorId = :sectorId
               and z.held + z.sold + :n <= z.capacity""")
    int tryHold(UUID eventId, UUID sectorId, int n);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ZoneStock z set z.held = z.held - :n, z.sold = z.sold + :n, z.version = z.version + 1
             where z.eventId = :eventId and z.sectorId = :sectorId and z.held >= :n""")
    int confirmHold(UUID eventId, UUID sectorId, int n);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ZoneStock z set z.held = z.held - :n, z.version = z.version + 1
             where z.eventId = :eventId and z.sectorId = :sectorId and z.held >= :n""")
    int releaseHold(UUID eventId, UUID sectorId, int n);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ZoneStock z set z.sold = z.sold - :n, z.version = z.version + 1
             where z.eventId = :eventId and z.sectorId = :sectorId and z.sold >= :n""")
    int releaseSold(UUID eventId, UUID sectorId, int n);

    List<ZoneStock> findByEventId(UUID eventId);

    @Query("select case when count(z) > 0 then true else false end from ZoneStock z "
            + "where z.eventId = :eventId and z.held + z.sold < z.capacity")
    boolean existsAvailable(UUID eventId);
}
