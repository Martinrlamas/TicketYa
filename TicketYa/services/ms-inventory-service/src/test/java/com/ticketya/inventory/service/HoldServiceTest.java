package com.ticketya.inventory.service;

import static com.ticketya.inventory.TestFixtures.general;
import static com.ticketya.inventory.TestFixtures.init;
import static com.ticketya.inventory.TestFixtures.numbered;
import static com.ticketya.inventory.TestFixtures.seatHold;
import static com.ticketya.inventory.TestFixtures.zoneHold;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ticketya.inventory.AbstractPostgresTest;
import com.ticketya.inventory.client.CatalogEventStatus;
import com.ticketya.inventory.dto.request.CreateHoldRequest;
import com.ticketya.inventory.dto.request.InitInventoryRequest.RowInit;
import com.ticketya.inventory.dto.request.SoldReleaseRequest;
import com.ticketya.inventory.dto.request.StockItemRequest;
import com.ticketya.inventory.entity.HoldStatus;
import com.ticketya.inventory.exception.HoldExpiredException;
import com.ticketya.inventory.exception.InventoryException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HoldServiceTest extends AbstractPostgresTest {

    @Autowired InventoryInitService initService;
    @Autowired HoldService holdService;
    @Autowired HoldExpirationService expirationService;
    @Autowired SoldReleaseService soldReleaseService;
    @Autowired InventoryCloseService closeService;

    // ---------- helpers ----------

    private UUID seatId(UUID event, String row, int number) {
        return jdbc.queryForObject(
                "select id from event_seat where event_id = ? and row_label = ? and seat_number = ?",
                UUID.class, event, row, number);
    }

    private String seatStatus(UUID seatId) {
        return jdbc.queryForObject("select status from event_seat where id = ?", String.class, seatId);
    }

    private String holdStatus(UUID holdId) {
        return jdbc.queryForObject("select status from hold where id = ?", String.class, holdId);
    }

    private int holdCount(UUID event) {
        return jdbc.queryForObject("select count(*) from hold where event_id = ?", Integer.class, event);
    }

    private void expireInDb(UUID holdId) {
        jdbc.update("update hold set expires_at = now() - interval '1 minute' where id = ?", holdId);
    }

    private UUID numberedEvent(UUID sector, int seatsInRowA) {
        UUID event = uuid();
        initService.init(event, init(numbered(sector, "Platea", new RowInit("A", seatsInRowA))));
        return event;
    }

    // ---------- 3.2 concurrencia sobre un asiento numerado ----------

    @Test
    void fiftyThreadsOnSameSeatYieldExactlyOneSuccess() throws Exception {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 10);
        UUID seat = seatId(event, "A", 5);

        Map<String, Integer> result = ConcurrencyHelper.race(50,
                i -> holdService.create(seatHold(event, uuid(), sector, seat)));

        assertThat(result).containsEntry("OK", 1).containsEntry("SEAT_UNAVAILABLE", 49);
        assertThat(seatStatus(seat)).isEqualTo("HELD");
        assertThat(holdCount(event)).isEqualTo(1);      // los 49 perdedores revirtieron también su INSERT de hold
    }

    // ---------- etiquetas de asiento en la respuesta del hold ----------

    @Test
    void holdResultListsSeatLabelsAndRetryReturnsSameSeats() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 10);
        UUID order = uuid();
        UUID a2 = seatId(event, "A", 2);
        UUID a1 = seatId(event, "A", 1);

        var first = holdService.create(seatHold(event, order, sector, a2, a1));
        var retry = holdService.create(seatHold(event, order, sector, a2, a1));

        assertThat(first.seats()).extracting("label").containsExactly("A-1", "A-2");
        assertThat(retry.created()).isFalse();
        assertThat(retry.seats()).extracting("id").containsExactlyInAnyOrder(a1, a2);
    }

    @Test
    void zoneHoldHasNoSeatLabels() {
        UUID zone = uuid();
        UUID event = uuid();
        initService.init(event, init(general(zone, "Campo", 10)));

        assertThat(holdService.create(zoneHold(event, uuid(), zone, 2)).seats()).isEmpty();
    }

    // ---------- 3.3 concurrencia sobre una zona ----------

    @Test
    void hundredThreadsOnZoneWithCapacityTenYieldExactlyTenSuccesses() throws Exception {
        UUID zone = uuid();
        UUID event = uuid();
        initService.init(event, init(general(zone, "Campo", 10)));

        Map<String, Integer> result = ConcurrencyHelper.race(100,
                i -> holdService.create(zoneHold(event, uuid(), zone, 1)));

        assertThat(result).containsEntry("OK", 10).containsEntry("INSUFFICIENT_CAPACITY", 90);
        var row = jdbc.queryForMap("select held, sold, capacity from zone_stock where event_id = ?", event);
        assertThat(((Number) row.get("held")).intValue() + ((Number) row.get("sold")).intValue()).isEqualTo(10);
    }

    // ---------- 3.5 todo-o-nada ----------

    @Test
    void requestWithOneTakenSeatHoldsNone() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 5);
        UUID a1 = seatId(event, "A", 1), a2 = seatId(event, "A", 2), a3 = seatId(event, "A", 3);
        jdbc.update("update event_seat set status = 'SOLD' where id = ?", a2);
        UUID order = uuid();

        assertThatThrownBy(() -> holdService.create(seatHold(event, order, sector, a1, a2, a3)))
                .isInstanceOf(InventoryException.class)
                .extracting("code").isEqualTo("SEAT_UNAVAILABLE");

        assertThat(seatStatus(a1)).isEqualTo("AVAILABLE");
        assertThat(seatStatus(a3)).isEqualTo("AVAILABLE");
        assertThat(holdCount(event)).isZero();
    }

    @Test
    void zoneRequestOverCapacityLeavesCounterUntouched() {
        UUID zone = uuid();
        UUID event = uuid();
        initService.init(event, init(general(zone, "Campo", 3)));

        assertThatThrownBy(() -> holdService.create(zoneHold(event, uuid(), zone, 5)))
                .isInstanceOf(InventoryException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_CAPACITY");

        assertThat(jdbc.queryForObject("select held from zone_stock where event_id = ?", Integer.class, event)).isZero();
    }

    // ---------- 3.7 idempotencia ----------

    @Test
    void sameOrderIdReturnsSameHoldWithoutHoldingMore() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 5);
        UUID order = uuid();
        UUID seat = seatId(event, "A", 1);

        var first = holdService.create(seatHold(event, order, sector, seat));
        var second = holdService.create(seatHold(event, order, sector, seat));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.hold().getId()).isEqualTo(first.hold().getId());
        assertThat(second.hold().getExpiresAt()).isEqualTo(first.hold().getExpiresAt());
        assertThat(holdCount(event)).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithSameOrderIdCreateOneHold() throws Exception {
        UUID zone = uuid();
        UUID event = uuid();
        initService.init(event, init(general(zone, "Campo", 100)));
        UUID order = uuid();

        Map<String, Integer> result = ConcurrencyHelper.race(20,
                i -> holdService.create(zoneHold(event, order, zone, 2)));

        assertThat(result).containsOnlyKeys("OK").containsEntry("OK", 20);
        assertThat(holdCount(event)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select held from zone_stock where event_id = ?", Integer.class, event)).isEqualTo(2);
    }

    // ---------- TTL ----------

    @Test
    void defaultTtlIsTenMinutes() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 2);

        var hold = holdService.create(seatHold(event, uuid(), sector, seatId(event, "A", 1))).hold();

        assertThat(Duration.between(hold.getCreatedAt(), hold.getExpiresAt()).abs())
                .isBetween(Duration.ofMinutes(10).minusSeconds(5), Duration.ofMinutes(10).plusSeconds(5));
    }

    // ---------- confirmación ----------

    @Test
    void confirmInTimeSellsSeatsAndZoneUnits() {
        UUID sector = uuid(), zone = uuid(), event = uuid();
        initService.init(event, init(numbered(sector, "Platea", new RowInit("A", 3)), general(zone, "Campo", 10)));
        UUID seat = seatId(event, "A", 1);
        var req = new CreateHoldRequest(event, uuid(), null, List.of(
                new StockItemRequest(sector, List.of(seat), null),
                new StockItemRequest(zone, null, 2)));
        UUID holdId = holdService.create(req).hold().getId();

        var confirmed = holdService.confirm(holdId);

        assertThat(confirmed.getStatus()).isEqualTo(HoldStatus.CONFIRMED);
        assertThat(seatStatus(seat)).isEqualTo("SOLD");
        var z = jdbc.queryForMap("select held, sold from zone_stock where event_id = ?", event);
        assertThat(z).containsEntry("held", 0).containsEntry("sold", 2);
    }

    @Test
    void confirmTwiceIsIdempotent() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 2);
        UUID holdId = holdService.create(seatHold(event, uuid(), sector, seatId(event, "A", 1))).hold().getId();

        holdService.confirm(holdId);
        var again = holdService.confirm(holdId);

        assertThat(again.getStatus()).isEqualTo(HoldStatus.CONFIRMED);
    }

    // ---------- 3.6 expiración ----------

    @Test
    void lateConfirmAnswersHoldExpiredAndReleasesSeats() {
        UUID sector = uuid(), zone = uuid(), event = uuid();
        initService.init(event, init(numbered(sector, "Platea", new RowInit("A", 3)), general(zone, "Campo", 10)));
        UUID seat = seatId(event, "A", 1);
        UUID holdId = holdService.create(new CreateHoldRequest(event, uuid(), null, List.of(
                new StockItemRequest(sector, List.of(seat), null),
                new StockItemRequest(zone, null, 4)))).hold().getId();
        expireInDb(holdId);

        assertThatThrownBy(() -> holdService.confirm(holdId)).isInstanceOf(HoldExpiredException.class);

        // noRollbackFor: la liberación sobrevive al 410
        assertThat(seatStatus(seat)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("select held from zone_stock where event_id = ?", Integer.class, event)).isZero();
        assertThat(holdStatus(holdId)).isEqualTo("EXPIRED");
    }

    @Test
    void expirationJobReleasesExpiredHolds() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 2);
        UUID seat = seatId(event, "A", 1);
        UUID holdId = holdService.create(seatHold(event, uuid(), sector, seat)).hold().getId();
        expireInDb(holdId);

        int expired = expirationService.expirarLote(100);

        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(holdStatus(holdId)).isEqualTo("EXPIRED");
        assertThat(seatStatus(seat)).isEqualTo("AVAILABLE");
        // y es idempotente: una segunda pasada no encuentra ese hold
        expirationService.expirarLote(100);
        assertThat(holdStatus(holdId)).isEqualTo("EXPIRED");
    }

    // ---------- liberación ----------

    @Test
    void releaseFreesSeatAndIsIdempotent() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 2);
        UUID seat = seatId(event, "A", 1);
        UUID holdId = holdService.create(seatHold(event, uuid(), sector, seat)).hold().getId();

        holdService.release(holdId);
        holdService.release(holdId);     // 204 sin efectos adicionales

        assertThat(seatStatus(seat)).isEqualTo("AVAILABLE");
        assertThat(holdStatus(holdId)).isEqualTo("RELEASED");
    }

    @Test
    void confirmOfReleasedHoldIsInvalidTransition() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 2);
        UUID holdId = holdService.create(seatHold(event, uuid(), sector, seatId(event, "A", 1))).hold().getId();
        holdService.release(holdId);

        assertThatThrownBy(() -> holdService.confirm(holdId))
                .isInstanceOf(InventoryException.class)
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
    }

    // ---------- cierre ----------

    @Test
    void closedEventRejectsNewHolds() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 2);
        closeService.close(event);
        closeService.close(event);   // idempotente

        assertThatThrownBy(() -> holdService.create(seatHold(event, uuid(), sector, seatId(event, "A", 1))))
                .isInstanceOf(InventoryException.class)
                .extracting("code").isEqualTo("EVENT_NOT_PURCHASABLE");
    }

    // ---------- devolución de lugares vendidos ----------

    @Test
    void soldReleaseReturnsSeatAndIsIdempotentByReleaseId() {
        UUID sector = uuid(), zone = uuid(), event = uuid();
        initService.init(event, init(numbered(sector, "Platea", new RowInit("A", 3)), general(zone, "Campo", 10)));
        UUID seat = seatId(event, "A", 1);
        UUID holdId = holdService.create(new CreateHoldRequest(event, uuid(), null, List.of(
                new StockItemRequest(sector, List.of(seat), null),
                new StockItemRequest(zone, null, 2)))).hold().getId();
        holdService.confirm(holdId);

        UUID releaseId = uuid();
        var req = new SoldReleaseRequest(releaseId, List.of(
                new StockItemRequest(sector, List.of(seat), null),
                new StockItemRequest(zone, null, 1)));

        var first = soldReleaseService.release(event, req);
        var repeated = soldReleaseService.release(event, req);

        assertThat(first.released()).isEqualTo(2);
        assertThat(repeated.released()).isEqualTo(2);                       // mismo resultado, sin liberar de nuevo
        assertThat(seatStatus(seat)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("select sold from zone_stock where event_id = ?", Integer.class, event))
                .isEqualTo(1);                                              // 2 vendidos - 1 devuelto, una sola vez
    }

    // ---------- 2.7 SOLD_OUT / reapertura ----------

    @Test
    void lastSeatNotifiesSoldOutAndReleaseNotifiesReopening() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 1);
        UUID holdId = holdService.create(seatHold(event, uuid(), sector, seatId(event, "A", 1))).hold().getId();

        verify(notifier).notifyStatus(event, CatalogEventStatus.SOLD_OUT);

        holdService.release(holdId);

        verify(notifier).notifyStatus(event, CatalogEventStatus.PUBLISHED);
    }

    @Test
    void noSoldOutNotificationWhileStockRemains() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 3);

        holdService.create(seatHold(event, uuid(), sector, seatId(event, "A", 1)));

        verify(notifier, never()).notifyStatus(event, CatalogEventStatus.SOLD_OUT);
    }

    @Test
    void createdHoldExpiresAtIsInTheFuture() {
        UUID sector = uuid();
        UUID event = numberedEvent(sector, 2);
        var hold = holdService.create(seatHold(event, uuid(), sector, seatId(event, "A", 1))).hold();
        assertThat(hold.getExpiresAt()).isAfter(Instant.now());
    }
}
