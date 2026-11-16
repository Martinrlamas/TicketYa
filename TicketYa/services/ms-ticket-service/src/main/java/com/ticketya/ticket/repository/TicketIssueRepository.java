package com.ticketya.ticket.repository;

import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Inserción idempotente de entradas. Se usa JDBC y no JPA porque:
 * (1) {@code ON CONFLICT (order_item_id) DO NOTHING} deja que la base decida el ganador cuando dos
 * solicitudes emiten el mismo ítem a la vez, y (2) {@code seat_id} puede ser NULL (zona general) y los
 * parámetros nulos en consultas nativas de Hibernate se enlazan como bytea.
 */
@Repository
public class TicketIssueRepository {

    private static final String INSERT = """
            insert into ticket(id, order_id, order_item_id, event_id, customer_id, sector_id, seat_id,
                               seat_label, qr_code, status, issued_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALID', ?)
            on conflict (order_item_id) do nothing""";

    private final JdbcTemplate jdbc;

    public TicketIssueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NewTicket(UUID orderId, UUID orderItemId, UUID eventId, UUID customerId, UUID sectorId,
                            UUID seatId, String seatLabel, String qrCode, Instant issuedAt) {}

    /** @return 1 si insertó; 0 si ese orderItemId ya tenía entrada (emitida antes o por otra solicitud concurrente). */
    public int insertIfAbsent(NewTicket t) {
        return jdbc.update(INSERT, ps -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, t.orderId());
            ps.setObject(3, t.orderItemId());
            ps.setObject(4, t.eventId());
            ps.setObject(5, t.customerId());
            ps.setObject(6, t.sectorId());
            ps.setObject(7, t.seatId(), Types.OTHER);       // admite null
            ps.setString(8, t.seatLabel());
            ps.setString(9, t.qrCode());
            ps.setObject(10, java.time.OffsetDateTime.ofInstant(t.issuedAt(), java.time.ZoneOffset.UTC));
        });
    }
}
