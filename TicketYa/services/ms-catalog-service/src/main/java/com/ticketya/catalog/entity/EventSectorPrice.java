package com.ticketya.catalog.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Precio de un sector para un evento. UNIQUE(event_id, sector_id) en la base. */
@Entity
@Table(name = "event_sector_price")
public class EventSectorPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    protected EventSectorPrice() {
        // JPA
    }

    EventSectorPrice(Event event, Sector sector, BigDecimal price, String currency) {
        this.event = event;
        this.sector = sector;
        this.price = price;
        this.currency = currency;
    }

    void cambiarPrecio(BigDecimal newPrice) {
        this.price = newPrice;
    }

    public Sector getSector() { return sector; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
}
