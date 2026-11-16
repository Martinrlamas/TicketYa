package com.ticketya.inventory.service;

import com.ticketya.inventory.dto.request.InitInventoryRequest;
import com.ticketya.inventory.dto.request.InitInventoryRequest.RowInit;
import com.ticketya.inventory.dto.request.InitInventoryRequest.SectorInit;
import com.ticketya.inventory.dto.response.InitInventoryResponse;
import com.ticketya.inventory.entity.SectorType;
import com.ticketya.inventory.exception.InventoryException;
import com.ticketya.inventory.repository.EventInventoryRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inicialización idempotente del inventario de un evento (la dispara catalog al publicar). */
@Service
public class InventoryInitService {

    public record InitResult(InitInventoryResponse body, boolean created) {}

    private static final String INSERT_SECTOR =
            "insert into event_sector(event_id, sector_id, type, name, position) values (?, ?, ?, ?, ?)";
    private static final String INSERT_SEAT = """
            insert into event_seat(id, event_id, sector_id, row_label, seat_number, status, version)
            values (?, ?, ?, ?, ?, 'AVAILABLE', 0)
            on conflict (event_id, sector_id, row_label, seat_number) do nothing""";
    private static final String INSERT_ZONE = """
            insert into zone_stock(event_id, sector_id, capacity, held, sold, version)
            values (?, ?, ?, 0, 0, 0)
            on conflict (event_id, sector_id) do nothing""";

    private final EventInventoryRepository inventoryRepo;
    private final JdbcTemplate jdbc;

    public InventoryInitService(EventInventoryRepository inventoryRepo, JdbcTemplate jdbc) {
        this.inventoryRepo = inventoryRepo;
        this.jdbc = jdbc;
    }

    @Transactional
    public InitResult init(UUID eventId, InitInventoryRequest req) {
        validateRequest(req);

        // Reclamo atómico: con dos inicializaciones concurrentes la segunda espera al commit de la primera y recibe 0.
        if (inventoryRepo.insertIfAbsent(eventId) == 0) {
            return new InitResult(new InitInventoryResponse(eventId, 0, 0), false);   // ya inicializado => 200
        }

        int seatsCreated = 0;
        int zonesCreated = 0;
        int position = 0;
        for (SectorInit sector : req.sectors()) {
            jdbc.update(INSERT_SECTOR, eventId, sector.sectorId(), sector.type().name(), sector.name(), position++);
            if (sector.type() == SectorType.NUMBERED) {
                seatsCreated += insertSeats(eventId, sector);
            } else {
                jdbc.update(INSERT_ZONE, eventId, sector.sectorId(), sector.capacity());
                zonesCreated++;
            }
        }
        return new InitResult(new InitInventoryResponse(eventId, seatsCreated, zonesCreated), true);
    }

    private int insertSeats(UUID eventId, SectorInit sector) {
        List<Object[]> rows = new ArrayList<>();
        for (RowInit row : sector.rows()) {
            for (int n = 1; n <= row.seatCount(); n++) {
                rows.add(new Object[] {UUID.randomUUID(), eventId, sector.sectorId(), row.rowLabel(), n});
            }
        }
        jdbc.batchUpdate(INSERT_SEAT, rows, 1000, (ps, a) -> {
            ps.setObject(1, a[0]);
            ps.setObject(2, a[1]);
            ps.setObject(3, a[2]);
            ps.setString(4, (String) a[3]);
            ps.setInt(5, (Integer) a[4]);
        });
        return rows.size();
    }

    private void validateRequest(InitInventoryRequest req) {
        Set<UUID> sectorIds = new HashSet<>();
        for (SectorInit s : req.sectors()) {
            if (!sectorIds.add(s.sectorId())) {
                throw InventoryException.validacion("sectorId repetido: " + s.sectorId());
            }
            if (s.type() == SectorType.NUMBERED) {
                Set<String> labels = new HashSet<>();
                for (RowInit r : s.rows()) {
                    if (!labels.add(r.rowLabel())) {
                        throw InventoryException.validacion("rowLabel repetido en el sector " + s.sectorId() + ": " + r.rowLabel());
                    }
                }
            }
        }
    }
}
