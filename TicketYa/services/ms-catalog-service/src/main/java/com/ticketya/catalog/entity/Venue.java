package com.ticketya.catalog.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "venue")
public class Venue {

    private static final BigDecimal LAT_MAX = new BigDecimal("90");
    private static final BigDecimal LON_MAX = new BigDecimal("180");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<Sector> sectors = new ArrayList<>();

    protected Venue() {
        // JPA
    }

    public static Venue create(String name, String address, String city, BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || latitude.abs().compareTo(LAT_MAX) > 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "La latitud debe estar entre -90 y 90");
        }
        if (longitude == null || longitude.abs().compareTo(LON_MAX) > 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "La longitud debe estar entre -180 y 180");
        }
        Venue v = new Venue();
        v.name = name;
        v.address = address;
        v.city = city;
        v.latitude = latitude;
        v.longitude = longitude;
        return v;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public List<Sector> getSectors() { return sectors; }
}
