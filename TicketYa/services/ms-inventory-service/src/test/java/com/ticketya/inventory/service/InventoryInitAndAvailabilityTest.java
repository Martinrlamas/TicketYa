package com.ticketya.inventory.service;

import static com.ticketya.inventory.TestFixtures.general;
import static com.ticketya.inventory.TestFixtures.init;
import static com.ticketya.inventory.TestFixtures.numbered;
import static com.ticketya.inventory.TestFixtures.seatHold;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.inventory.AbstractPostgresTest;
import com.ticketya.inventory.dto.request.InitInventoryRequest.RowInit;
import com.ticketya.inventory.dto.response.AvailabilityResponse;
import com.ticketya.inventory.entity.SeatStatus;
import com.ticketya.inventory.exception.InventoryException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InventoryInitAndAvailabilityTest extends AbstractPostgresTest {

    @Autowired InventoryInitService initService;
    @Autowired AvailabilityService availabilityService;
    @Autowired HoldService holdService;

    @Test
    void firstInitCreatesSeatsAndZoneCounter() {
        UUID event = uuid(), platea = uuid(), campo = uuid();

        var result = initService.init(event, init(
                numbered(platea, "Platea", new RowInit("A", 20), new RowInit("B", 20)),
                general(campo, "Campo", 5000)));

        assertThat(result.created()).isTrue();
        assertThat(result.body().seatsCreated()).isEqualTo(40);
        assertThat(result.body().zonesCreated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from event_seat where event_id = ? and status = 'AVAILABLE'",
                Integer.class, event)).isEqualTo(40);
        assertThat(jdbc.queryForMap("select capacity, held, sold from zone_stock where event_id = ?", event))
                .containsEntry("capacity", 5000).containsEntry("held", 0).containsEntry("sold", 0);
    }

    @Test
    void reinitIsIdempotent() {
        UUID event = uuid(), platea = uuid();
        var req = init(numbered(platea, "Platea", new RowInit("A", 10)));
        initService.init(event, req);

        var second = initService.init(event, req);

        assertThat(second.created()).isFalse();   // => 200
        assertThat(jdbc.queryForObject("select count(*) from event_seat where event_id = ?", Integer.class, event))
                .isEqualTo(10);
    }

    @Test
    void availabilityShowsHeldSeatsAsUnavailableAndZoneFreeCount() {
        UUID event = uuid(), platea = uuid(), campo = uuid();
        initService.init(event, init(numbered(platea, "Platea", new RowInit("A", 5)), general(campo, "Campo", 100)));
        UUID seat = jdbc.queryForObject(
                "select id from event_seat where event_id = ? and seat_number = 1", UUID.class, event);
        holdService.create(seatHold(event, uuid(), platea, seat));
        jdbc.update("update zone_stock set held = 10, sold = 30 where event_id = ?", event);

        AvailabilityResponse res = availabilityService.get(event);

        var numberedSector = res.sectors().get(0);
        assertThat(numberedSector.capacity()).isEqualTo(5);
        assertThat(numberedSector.available()).isEqualTo(4);
        assertThat(numberedSector.seats()).filteredOn(s -> s.seatId().equals(seat))
                .singleElement().extracting("status").isEqualTo(SeatStatus.HELD);
        var zone = res.sectors().get(1);
        assertThat(zone.available()).isEqualTo(60);          // 100 - 30 vendidos - 10 retenidos
        assertThat(zone.seats()).isNull();
    }

    @Test
    void availabilityOfUnknownEventIsNotFound() {
        assertThatThrownBy(() -> availabilityService.get(uuid()))
                .isInstanceOf(InventoryException.class)
                .extracting("code").isEqualTo("NOT_FOUND");
    }

    @Test
    void duplicatedSectorIdsAreRejected() {
        UUID s = uuid();
        assertThatThrownBy(() -> initService.init(uuid(), init(general(s, "A", 1), general(s, "B", 2))))
                .isInstanceOf(InventoryException.class)
                .extracting("code").isEqualTo("VALIDATION_ERROR");
    }
}
