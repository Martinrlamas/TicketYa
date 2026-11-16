package com.ticketya.inventory.service;

import com.ticketya.inventory.exception.InventoryException;
import com.ticketya.inventory.repository.EventInventoryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCloseService {

    private final EventInventoryRepository inventoryRepo;

    public InventoryCloseService(EventInventoryRepository inventoryRepo) {
        this.inventoryRepo = inventoryRepo;
    }

    /** Idempotente. Los holds ya creados siguen su curso; solo se bloquean los nuevos. */
    @Transactional
    public void close(UUID eventId) {
        inventoryRepo.findById(eventId)
                .orElseThrow(() -> InventoryException.noEncontrado("El evento " + eventId + " no tiene inventario"))
                .close();
    }
}
