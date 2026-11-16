package com.ticketya.catalog.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;

/** Tarea 3.1 y 3.2: máquina de estados, precios y validaciones. Sin Spring ni base. */
class EventTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
    static final UUID ORG = UUID.randomUUID();

    private final Venue venue = Venue.create("Estadio", "Av. 1", "Tandil", new BigDecimal("-37.32"), new BigDecimal("-59.13"));
    private final Sector campo = Sector.general(venue, "Campo", 5000);

    private Event draft() {
        return Event.draft(ORG, venue, "Show", "desc", EventType.CONCERT, NOW.plusDays(30), CLOCK);
    }

    private Event withPrices() {
        Event e = draft();
        e.reemplazarPrecios(Map.of(campo, new BigDecimal("30000")));
        return e;
    }

    private Event inStatus(EventStatus target) {
        Event e = withPrices();
        switch (target) {
            case DRAFT -> { }
            case PUBLISHED -> e.marcarPublicado();
            case SOLD_OUT -> { e.marcarPublicado(); e.soldOut(); }
            case SUSPENDED -> { e.marcarPublicado(); e.suspend(); }
            case CANCELLED -> e.cancel();
            case FINISHED -> throw new IllegalArgumentException("FINISHED lo asigna el job");
        }
        assertThat(e.getStatus()).isEqualTo(target);
        return e;
    }

    private void assertInvalidTransition(Event e, Consumer<Event> action) {
        EventStatus before = e.getStatus();
        assertThatThrownBy(() -> action.accept(e)).isInstanceOfSatisfying(PlatformException.class,
                ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        assertThat(e.getStatus()).isEqualTo(before);
    }

    // ------------------------------------------------------------------ creación

    @Test
    void newEventIsDraftAndOwnedByCreator() {
        Event e = draft();
        assertThat(e.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(e.esDuenio(ORG)).isTrue();
        assertThat(e.esDuenio(UUID.randomUUID())).isFalse();
    }

    @Test
    void pastDateIsValidationError() {
        assertThatThrownBy(() -> Event.draft(ORG, venue, "Show", null, EventType.CONCERT, NOW.minusDays(1), CLOCK))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // ------------------------------------------------------------------ publicación

    @Test
    void publishWithoutPricesIsRejected() {
        assertThatThrownBy(() -> draft().assertPublishable(CLOCK))
                .isInstanceOfSatisfying(PlatformException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
                    assertThat(ex.getMessage()).contains("no tiene sectores con precio");
                });
    }

    @Test
    void assertPublishableDoesNotChangeStatus() {
        Event e = withPrices();
        e.assertPublishable(CLOCK);
        assertThat(e.getStatus()).isEqualTo(EventStatus.DRAFT);
        e.marcarPublicado();
        assertThat(e.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publishWhenDateAlreadyPassedIsRejected() {
        Event e = withPrices();
        Clock later = Clock.fixed(Instant.parse("2027-06-01T00:00:00Z"), ZoneOffset.UTC);
        assertThatThrownBy(() -> e.assertPublishable(later)).isInstanceOfSatisfying(PlatformException.class,
                ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    // ------------------------------------------------------------------ transiciones válidas

    @Test
    void validTransitions() {
        Event e = inStatus(EventStatus.PUBLISHED);
        e.soldOut();
        assertThat(e.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
        e.reopen();
        assertThat(e.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        e.suspend();
        assertThat(e.getStatus()).isEqualTo(EventStatus.SUSPENDED);
        e.resume();
        assertThat(e.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void soldOutEventCanBeSuspended() {
        Event e = inStatus(EventStatus.SOLD_OUT);
        e.suspend();
        assertThat(e.getStatus()).isEqualTo(EventStatus.SUSPENDED);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"DRAFT", "PUBLISHED", "SOLD_OUT", "SUSPENDED"})
    void cancelIsAllowedFromAnyNonTerminalState(EventStatus from) {
        Event e = inStatus(from);
        e.cancel();
        assertThat(e.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    // ------------------------------------------------------------------ transiciones inválidas

    @Test
    void cancelledEventIsTerminal() {
        Event e = inStatus(EventStatus.CANCELLED);
        assertInvalidTransition(e, Event::cancel);
        assertInvalidTransition(e, Event::suspend);
        assertInvalidTransition(e, Event::resume);
        assertInvalidTransition(e, Event::marcarPublicado);
        assertInvalidTransition(e, Event::soldOut);
        assertInvalidTransition(e, Event::reopen);
    }

    @Test
    void draftCannotBeSuspendedResumedSoldOutOrReopened() {
        Event e = inStatus(EventStatus.DRAFT);
        assertInvalidTransition(e, Event::suspend);
        assertInvalidTransition(e, Event::resume);
        assertInvalidTransition(e, Event::soldOut);
        assertInvalidTransition(e, Event::reopen);
    }

    @Test
    void publishedCannotBePublishedAgainResumedOrReopened() {
        Event e = inStatus(EventStatus.PUBLISHED);
        assertInvalidTransition(e, Event::marcarPublicado);
        assertInvalidTransition(e, Event::resume);
        assertInvalidTransition(e, Event::reopen);
    }

    @Test
    void suspendedCannotBeSoldOutOrSuspendedAgain() {
        Event e = inStatus(EventStatus.SUSPENDED);
        assertInvalidTransition(e, Event::soldOut);
        assertInvalidTransition(e, Event::suspend);
    }

    @Test
    void editingAndPricingOnlyAllowedInDraft() {
        Event e = inStatus(EventStatus.PUBLISHED);
        assertInvalidTransition(e, x -> x.reemplazarPrecios(Map.of(campo, BigDecimal.TEN)));
        assertInvalidTransition(e, x -> x.update(venue, "n", "d", EventType.OTHER, NOW.plusDays(5), CLOCK));
        assertInvalidTransition(e, x -> x.reemplazarParticipantes(List.of()));
    }

    // ------------------------------------------------------------------ estado informado por inventory

    @Test
    void inventoryStatusIsIdempotentAndRestricted() {
        Event e = inStatus(EventStatus.PUBLISHED);
        e.applyInventoryStatus(EventStatus.PUBLISHED); // ya estaba: sin cambios
        e.applyInventoryStatus(EventStatus.SOLD_OUT);
        e.applyInventoryStatus(EventStatus.SOLD_OUT);
        assertThat(e.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
        e.applyInventoryStatus(EventStatus.PUBLISHED);
        assertThat(e.getStatus()).isEqualTo(EventStatus.PUBLISHED);

        assertThatThrownBy(() -> e.applyInventoryStatus(EventStatus.CANCELLED)).isInstanceOfSatisfying(
                PlatformException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertInvalidTransition(inStatus(EventStatus.DRAFT), x -> x.applyInventoryStatus(EventStatus.SOLD_OUT));
    }

    // ------------------------------------------------------------------ visibilidad / compra

    @Test
    void visibilityAndPurchasability() {
        assertThat(inStatus(EventStatus.DRAFT).esVisiblePublicamente()).isFalse();
        assertThat(inStatus(EventStatus.PUBLISHED).esVisiblePublicamente()).isTrue();
        assertThat(inStatus(EventStatus.SOLD_OUT).esVisiblePublicamente()).isTrue();
        assertThat(inStatus(EventStatus.SUSPENDED).esVisiblePublicamente()).isFalse();
        assertThat(inStatus(EventStatus.CANCELLED).esVisiblePublicamente()).isFalse();

        assertThat(inStatus(EventStatus.PUBLISHED).esComprable(CLOCK)).isTrue();
        assertThat(inStatus(EventStatus.SUSPENDED).esComprable(CLOCK)).isFalse();
        assertThat(inStatus(EventStatus.DRAFT).esComprable(CLOCK)).isFalse();
        Clock after = Clock.fixed(Instant.parse("2027-06-01T00:00:00Z"), ZoneOffset.UTC);
        assertThat(inStatus(EventStatus.PUBLISHED).esComprable(after)).isFalse();
    }

    // ------------------------------------------------------------------ precios

    @Test
    void pricesSetMinPrice() {
        Venue v = venue;
        Sector platea = Sector.numbered(v, "Platea", List.of(new SectorRow("A", 20)));
        Event e = draft();
        Map<Sector, BigDecimal> prices = new LinkedHashMap<>();
        prices.put(platea, new BigDecimal("45000"));
        prices.put(campo, new BigDecimal("30000"));
        e.reemplazarPrecios(prices);

        assertThat(e.getPrices()).hasSize(2);
        assertThat(e.getMinPrice()).isEqualByComparingTo("30000");
    }

    @Test
    void replacingPricesUpdatesInPlaceAndDropsMissingSectors() {
        Sector platea = Sector.numbered(venue, "Platea", List.of(new SectorRow("A", 20)));
        Event e = draft();
        e.reemplazarPrecios(Map.of(platea, new BigDecimal("45000"), campo, new BigDecimal("30000")));

        e.reemplazarPrecios(Map.of(campo, new BigDecimal("25000")));

        assertThat(e.getPrices()).hasSize(1);
        assertThat(e.getPrices().get(0).getSector()).isSameAs(campo);
        assertThat(e.getPrices().get(0).getPrice()).isEqualByComparingTo("25000");
        assertThat(e.getMinPrice()).isEqualByComparingTo("25000");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "-5"})
    void nonPositivePriceIsRejected(String price) {
        assertThatThrownBy(() -> draft().reemplazarPrecios(Map.of(campo, new BigDecimal(price))))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void sectorFromAnotherVenueIsRejected() {
        Venue other = Venue.create("Otro", "Calle 2", "Azul", new BigDecimal("-36.7"), new BigDecimal("-59.8"));
        Sector foreign = Sector.general(other, "Popular", 100);
        assertThatThrownBy(() -> draft().reemplazarPrecios(Map.of(foreign, BigDecimal.TEN)))
                .isInstanceOfSatisfying(PlatformException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void changingVenueDropsPrices() {
        Event e = withPrices();
        Venue other = Venue.create("Otro", "Calle 2", "Azul", new BigDecimal("-36.7"), new BigDecimal("-59.8"));
        e.update(other, "Show", "d", EventType.CONCERT, NOW.plusDays(10), CLOCK);
        assertThat(e.getPrices()).isEmpty();
        assertThat(e.getMinPrice()).isNull();
    }
}
