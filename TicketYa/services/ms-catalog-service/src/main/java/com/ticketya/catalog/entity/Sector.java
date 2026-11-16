package com.ticketya.catalog.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

/** Sector de un lugar. La consistencia numerado/general se garantiza en los factory methods. */
@Entity
@Table(name = "sector")
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SectorType type;

    /** Solo GENERAL; en NUMBERED es NULL y la capacidad se deriva de las filas. */
    private Integer capacity;

    @OneToMany(mappedBy = "sector", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowLabel ASC")
    private List<SectorRow> rows = new ArrayList<>();

    protected Sector() {
        // JPA
    }

    private Sector(Venue venue, String name, SectorType type, Integer capacity) {
        this.venue = venue;
        this.name = name;
        this.type = type;
        this.capacity = capacity;
    }

    public static Sector numbered(Venue venue, String name, List<SectorRow> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Un sector numerado requiere filas con asientos");
        }
        Set<String> labels = new HashSet<>();
        for (SectorRow r : rows) {
            if (r.getSeatCount() <= 0) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Un sector numerado requiere filas con asientos");
            }
            if (!labels.add(r.getRowLabel().toLowerCase())) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "La fila " + r.getRowLabel() + " está repetida");
            }
        }
        Sector s = new Sector(venue, name, SectorType.NUMBERED, null);
        rows.forEach(s::agregarFila);
        return s;
    }

    public static Sector general(Venue venue, String name, Integer capacity) {
        if (capacity == null || capacity <= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Un sector general requiere capacidad > 0");
        }
        return new Sector(venue, name, SectorType.GENERAL, capacity);
    }

    private void agregarFila(SectorRow row) {
        row.attachTo(this);
        rows.add(row);
    }

    public int capacidadTotal() {
        return type == SectorType.GENERAL ? capacity : rows.stream().mapToInt(SectorRow::getSeatCount).sum();
    }

    public boolean belongsTo(Venue other) {
        return venue == other || (venue.getId() != null && venue.getId().equals(other.getId()));
    }

    public UUID getId() { return id; }
    public Venue getVenue() { return venue; }
    public String getName() { return name; }
    public SectorType getType() { return type; }
    public Integer getCapacity() { return capacity; }
    public List<SectorRow> getRows() { return rows; }
}
