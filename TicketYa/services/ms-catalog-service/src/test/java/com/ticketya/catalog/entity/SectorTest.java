package com.ticketya.catalog.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;

class SectorTest {

    private final Venue venue = Venue.create("Estadio", "Av. 1", "Tandil", new BigDecimal("-37.32"), new BigDecimal("-59.13"));

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PlatformException.class,
                ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void numberedSectorSumsRowSeats() {
        Sector s = Sector.numbered(venue, "Platea", List.of(new SectorRow("A", 20), new SectorRow("B", 20)));
        assertThat(s.getType()).isEqualTo(SectorType.NUMBERED);
        assertThat(s.getCapacity()).isNull();
        assertThat(s.capacidadTotal()).isEqualTo(40);
        assertThat(s.getRows()).hasSize(2);
    }

    @Test
    void generalSectorKeepsCapacity() {
        Sector s = Sector.general(venue, "Campo", 5000);
        assertThat(s.capacidadTotal()).isEqualTo(5000);
        assertThat(s.getRows()).isEmpty();
    }

    @Test
    void numberedWithoutRowsIsRejected() {
        assertValidation(() -> Sector.numbered(venue, "Platea", List.of()));
        assertValidation(() -> Sector.numbered(venue, "Platea", null));
    }

    @Test
    void rowWithoutSeatsIsRejected() {
        assertValidation(() -> new SectorRow("A", 0));
        assertValidation(() -> new SectorRow(" ", 10));
    }

    @Test
    void duplicatedRowLabelsAreRejected() {
        assertValidation(() -> Sector.numbered(venue, "Platea", List.of(new SectorRow("A", 5), new SectorRow("a", 5))));
    }

    @Test
    void generalWithoutPositiveCapacityIsRejected() {
        assertValidation(() -> Sector.general(venue, "Campo", null));
        assertValidation(() -> Sector.general(venue, "Campo", 0));
    }

    @Test
    void venueCoordinatesMustBeInRange() {
        assertValidation(() -> Venue.create("X", "Y", "Z", new BigDecimal("91"), BigDecimal.ZERO));
        assertValidation(() -> Venue.create("X", "Y", "Z", BigDecimal.ZERO, new BigDecimal("-181")));
    }
}
