package com.ticketya.catalog.entity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Evento con su máquina de estados:
 * <pre>
 * DRAFT -> PUBLISHED (organizador dueño, tras inicializar inventario)
 * PUBLISHED <-> SOLD_OUT (inventory)        PUBLISHED/SOLD_OUT <-> SUSPENDED (ADMIN)
 * DRAFT/PUBLISHED/SOLD_OUT/SUSPENDED -> CANCELLED (ADMIN)      PUBLISHED/SOLD_OUT -> FINISHED (job)
 * </pre>
 */
@Entity
@Table(name = "event")
public class Event {

    public static final String CURRENCY = "ARS";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType type;

    /** Referencia a identidad (E2). Sin FK. */
    @Column(name = "organizer_id", nullable = false, updatable = false)
    private UUID organizerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    /** Desnormalizado para ordenar por precio sin joins. */
    @Column(name = "min_price", precision = 12, scale = 2)
    private BigDecimal minPrice;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<EventParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventSectorPrice> prices = new ArrayList<>();

    protected Event() {
        // JPA
    }

    public static Event draft(UUID organizerId, Venue venue, String name, String description, EventType type,
            OffsetDateTime startsAt, Clock clock) {
        exigirFuturo(startsAt, clock, ErrorCode.VALIDATION_ERROR, "La fecha del evento debe ser futura");
        Event e = new Event();
        e.organizerId = organizerId;
        e.venue = venue;
        e.name = name;
        e.description = description;
        e.type = type;
        e.startsAt = startsAt;
        e.createdAt = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        return e;
    }

    // ------------------------------------------------------------------ edición (solo DRAFT)

    public void update(Venue newVenue, String name, String description, EventType type, OffsetDateTime startsAt,
            Clock clock) {
        exigirEstado(EventStatus.DRAFT);
        exigirFuturo(startsAt, clock, ErrorCode.VALIDATION_ERROR, "La fecha del evento debe ser futura");
        if (!sameVenue(newVenue)) {
            prices.clear(); // los sectores del lugar anterior ya no valen
            minPrice = null;
        }
        this.venue = newVenue;
        this.name = name;
        this.description = description;
        this.type = type;
        this.startsAt = startsAt;
    }

    public void reemplazarParticipantes(List<ParticipantData> data) {
        exigirEstado(EventStatus.DRAFT);
        participants.clear();
        if (data != null) {
            data.forEach(p -> agregarParticipante(p.name(), p.role()));
        }
    }

    public void agregarParticipante(String participantName, ParticipantRole role) {
        participants.add(new EventParticipant(this, participantName, role));
    }

    /**
     * Reemplaza el conjunto de precios. Solo DRAFT; precios > 0 y sectores del lugar del evento.
     * Actualiza en el lugar los precios existentes (evita borrar+insertar la misma clave en un flush).
     */
    public void reemplazarPrecios(Map<Sector, BigDecimal> newPrices) {
        exigirEstado(EventStatus.DRAFT);
        if (newPrices == null || newPrices.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Debe informar al menos un precio");
        }
        newPrices.forEach((sector, price) -> {
            if (!sector.belongsTo(venue)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "El sector " + sector.getName() + " no pertenece al lugar del evento");
            }
            if (price == null || price.signum() <= 0) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "El precio debe ser > 0");
            }
        });

        prices.removeIf(p -> newPrices.keySet().stream().noneMatch(s -> sameSector(p.getSector(), s)));
        newPrices.forEach((sector, price) -> {
            EventSectorPrice current = prices.stream()
                    .filter(p -> sameSector(p.getSector(), sector)).findFirst().orElse(null);
            if (current != null) {
                current.cambiarPrecio(price);
            } else {
                prices.add(new EventSectorPrice(this, sector, price, CURRENCY));
            }
        });
        minPrice = prices.stream().map(EventSectorPrice::getPrice).min(Comparator.naturalOrder()).orElse(null);
    }

    // ------------------------------------------------------------------ transiciones

    /** Valida precondiciones; NO cambia el estado (eso ocurre después de inicializar inventario). */
    public void assertPublishable(Clock clock) {
        exigirEstado(EventStatus.DRAFT);
        if (prices.isEmpty()) {
            throw new PlatformException(ErrorCode.INVALID_STATE_TRANSITION, "El evento no tiene sectores con precio");
        }
        exigirFuturo(startsAt, clock, ErrorCode.INVALID_STATE_TRANSITION, "La fecha del evento ya pasó");
    }

    public void marcarPublicado() {
        exigirEstado(EventStatus.DRAFT);
        status = EventStatus.PUBLISHED;
    }

    public void suspend() {
        exigirAlgunEstado(EventStatus.PUBLISHED, EventStatus.SOLD_OUT);
        status = EventStatus.SUSPENDED;
    }

    public void resume() {
        exigirEstado(EventStatus.SUSPENDED);
        status = EventStatus.PUBLISHED;
    }

    public void cancel() {
        exigirAlgunEstado(EventStatus.DRAFT, EventStatus.PUBLISHED, EventStatus.SOLD_OUT, EventStatus.SUSPENDED);
        status = EventStatus.CANCELLED;
    }

    public void soldOut() {
        exigirEstado(EventStatus.PUBLISHED);
        status = EventStatus.SOLD_OUT;
    }

    public void reopen() {
        exigirEstado(EventStatus.SOLD_OUT);
        status = EventStatus.PUBLISHED;
    }

    /** Estado informado por inventory (SOLD_OUT / PUBLISHED). Repetir el estado actual es idempotente. */
    public void applyInventoryStatus(EventStatus target) {
        if (target == status && (target == EventStatus.SOLD_OUT || target == EventStatus.PUBLISHED)) {
            return;
        }
        switch (target) {
            case SOLD_OUT -> soldOut();
            case PUBLISHED -> reopen();
            default -> throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                    "inventory solo puede informar SOLD_OUT o PUBLISHED");
        }
    }

    // ------------------------------------------------------------------ consultas

    public boolean esDuenio(UUID userId) {
        return organizerId.equals(userId);
    }

    public boolean esVisiblePublicamente() {
        return status == EventStatus.PUBLISHED || status == EventStatus.SOLD_OUT;
    }

    /** A la venta: publicado (o agotado, donde inventory decide) y con fecha futura. */
    public boolean esComprable(Clock clock) {
        return esVisiblePublicamente() && startsAt.isAfter(OffsetDateTime.now(clock));
    }

    // ------------------------------------------------------------------ helpers

    private boolean sameVenue(Venue other) {
        return venue == other || (venue.getId() != null && venue.getId().equals(other.getId()));
    }

    private static boolean sameSector(Sector a, Sector b) {
        return a == b || (a.getId() != null && a.getId().equals(b.getId()));
    }

    private static void exigirFuturo(OffsetDateTime when, Clock clock, ErrorCode code, String message) {
        if (when == null || !when.isAfter(OffsetDateTime.now(clock))) {
            throw new PlatformException(code, message);
        }
    }

    private void exigirEstado(EventStatus expected) {
        exigirAlgunEstado(expected);
    }

    private void exigirAlgunEstado(EventStatus... allowed) {
        if (!Arrays.asList(allowed).contains(status)) {
            throw new PlatformException(ErrorCode.INVALID_STATE_TRANSITION, "No permitido en estado " + status);
        }
    }

    /** Dato de participante sin depender de DTOs. */
    public record ParticipantData(String name, ParticipantRole role) {
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public EventType getType() { return type; }
    public UUID getOrganizerId() { return organizerId; }
    public Venue getVenue() { return venue; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public EventStatus getStatus() { return status; }
    public BigDecimal getMinPrice() { return minPrice; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<EventParticipant> getParticipants() { return participants; }
    public List<EventSectorPrice> getPrices() { return prices; }
}
