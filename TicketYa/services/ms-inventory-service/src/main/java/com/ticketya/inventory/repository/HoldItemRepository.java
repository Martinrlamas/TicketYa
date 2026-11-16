package com.ticketya.inventory.repository;

import com.ticketya.inventory.entity.HoldItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldItemRepository extends JpaRepository<HoldItem, UUID> {

    List<HoldItem> findByHoldId(UUID holdId);
}
