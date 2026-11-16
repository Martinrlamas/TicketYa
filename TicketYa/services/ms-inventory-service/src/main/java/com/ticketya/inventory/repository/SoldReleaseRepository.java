package com.ticketya.inventory.repository;

import com.ticketya.inventory.entity.SoldRelease;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SoldReleaseRepository extends JpaRepository<SoldRelease, UUID> {

    /** Reclama el releaseId. 0 filas => ya fue aplicada (idempotente). */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into sold_release(release_id, event_id, released_count, created_at)
            values (:releaseId, :eventId, 0, now())
            on conflict (release_id) do nothing""")
    int insertIfAbsent(UUID releaseId, UUID eventId);

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = "update sold_release set released_count = :count where release_id = :releaseId")
    int updateCount(UUID releaseId, int count);
}
