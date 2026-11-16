package com.ticketya.catalog.entity;

import java.util.UUID;

import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sector_row")
public class SectorRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;

    @Column(name = "row_label", nullable = false, length = 10)
    private String rowLabel;

    @Column(name = "seat_count", nullable = false)
    private int seatCount;

    protected SectorRow() {
        // JPA
    }

    public SectorRow(String rowLabel, int seatCount) {
        if (rowLabel == null || rowLabel.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "La fila requiere una etiqueta");
        }
        if (seatCount <= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                    "La fila " + rowLabel + " debe tener más de 0 asientos");
        }
        this.rowLabel = rowLabel.trim();
        this.seatCount = seatCount;
    }

    void attachTo(Sector sector) {
        this.sector = sector;
    }

    public UUID getId() { return id; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatCount() { return seatCount; }
}
