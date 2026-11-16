package com.ticketya.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ticketya.catalog.client.InventoryClient;
import com.ticketya.catalog.client.OrderClient;
import com.ticketya.catalog.client.dto.InventorySectorDto;
import com.ticketya.catalog.dto.response.EventResponse;
import com.ticketya.catalog.dto.response.SaleInfoResponse;
import com.ticketya.catalog.mapper.EventMapper;
import com.ticketya.catalog.entity.Event;
import com.ticketya.catalog.entity.EventStatus;
import com.ticketya.catalog.entity.EventType;
import com.ticketya.catalog.entity.Sector;
import com.ticketya.catalog.entity.SectorRow;
import com.ticketya.catalog.entity.Venue;
import com.ticketya.catalog.repository.EventRepository;
import com.ticketya.catalog.repository.SectorRepository;
import com.ticketya.catalog.repository.VenueRepository;
import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;

/** Tarea 3.4 y reglas de dueño/visibilidad: se prueba el service con dependencias mockeadas. */
class EventServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    static final UUID ID = UUID.randomUUID();

    final EventRepository events = mock(EventRepository.class);
    final InventoryClient inventory = mock(InventoryClient.class);
    final OrderClient orders = mock(OrderClient.class);
    final Identity owner = new Identity(UUID.randomUUID(), Role.ORGANIZER);
    final Identity otherOrganizer = new Identity(UUID.randomUUID(), Role.ORGANIZER);
    final Identity admin = new Identity(UUID.randomUUID(), Role.ADMIN);

    EventService service;
    Venue venue;
    Sector platea;
    Sector campo;

    @BeforeEach
    void setUp() {
        service = new EventService(events, mock(VenueRepository.class), mock(SectorRepository.class),
                inventory, orders, new EventMapper(), CLOCK);
        venue = Venue.create("Estadio", "Av. 1", "Tandil", new BigDecimal("-37.32"), new BigDecimal("-59.13"));
        platea = Sector.numbered(venue, "Platea", List.of(new SectorRow("A", 20), new SectorRow("B", 20)));
        campo = Sector.general(venue, "Campo", 5000);
    }

    private Event eventWithPrices() {
        Event e = Event.draft(owner.userId(), venue, "Show", "d", EventType.CONCERT,
                OffsetDateTime.now(CLOCK).plusDays(30), CLOCK);
        e.reemplazarPrecios(Map.of(platea, new BigDecimal("45000"), campo, new BigDecimal("30000")));
        when(events.findById(ID)).thenReturn(Optional.of(e));
        return e;
    }

    // ------------------------------------------------------------------ publicación

    @Test
    void publishInitializesInventoryThenPublishes() {
        Event e = eventWithPrices();

        EventResponse res = service.publish(owner, ID);

        assertThat(res.status()).isEqualTo(EventStatus.PUBLISHED);
        ArgumentCaptor<List<InventorySectorDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(inventory).initialize(any(), captor.capture());
        List<InventorySectorDto> sent = captor.getValue();
        assertThat(sent).hasSize(2);
        InventorySectorDto numbered = sent.stream().filter(s -> s.type().equals("NUMBERED")).findFirst().orElseThrow();
        assertThat(numbered.rows()).hasSize(2);
        assertThat(numbered.capacity()).isNull();
        InventorySectorDto general = sent.stream().filter(s -> s.type().equals("GENERAL")).findFirst().orElseThrow();
        assertThat(general.capacity()).isEqualTo(5000);
        assertThat(general.rows()).isNull();
        assertThat(e.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publishKeepsDraftWhenInventoryIsDown() {
        Event e = eventWithPrices();
        doThrow(new PlatformException(ErrorCode.UPSTREAM_UNAVAILABLE, "down")).when(inventory).initialize(any(), any());

        assertThatThrownBy(() -> service.publish(owner, ID)).isInstanceOfSatisfying(PlatformException.class,
                ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
        assertThat(e.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void retryAfterInventoryFailureCompletesPublication() {
        Event e = eventWithPrices();
        doThrow(new PlatformException(ErrorCode.UPSTREAM_UNAVAILABLE, "down"))
                .doNothing().when(inventory).initialize(any(), any());

        assertThatThrownBy(() -> service.publish(owner, ID)).isInstanceOf(PlatformException.class);
        service.publish(owner, ID);

        assertThat(e.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publishWithoutPricesIs409AndNeverCallsInventory() {
        Event e = Event.draft(owner.userId(), venue, "Show", "d", EventType.CONCERT,
                OffsetDateTime.now(CLOCK).plusDays(30), CLOCK);
        when(events.findById(ID)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.publish(owner, ID)).isInstanceOfSatisfying(PlatformException.class,
                ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        verify(inventory, never()).initialize(any(), any());
        assertThat(e.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void anotherOrganizerCannotPublishOrPrice() {
        eventWithPrices();
        assertThatThrownBy(() -> service.publish(otherOrganizer, ID)).isInstanceOfSatisfying(PlatformException.class,
                ex -> assertThat(ex.code()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(inventory, never()).initialize(any(), any());
    }

    @Test
    void publishOfUnknownEventIs404() {
        when(events.findById(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.publish(owner, ID)).isInstanceOfSatisfying(PlatformException.class,
                ex -> assertThat(ex.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ------------------------------------------------------------------ visibilidad

    @Test
    void draftIsHiddenFromStrangersAndAnonymous() {
        eventWithPrices();
        for (Optional<Identity> viewer : List.of(Optional.<Identity>empty(), Optional.of(otherOrganizer),
                Optional.of(new Identity(UUID.randomUUID(), Role.CUSTOMER)))) {
            assertThatThrownBy(() -> service.obtenerVisible(viewer, ID)).isInstanceOfSatisfying(PlatformException.class,
                    ex -> assertThat(ex.code()).isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    @Test
    void draftIsVisibleToOwnerAndAdmin() {
        eventWithPrices();
        assertThat(service.obtenerVisible(Optional.of(owner), ID).status()).isEqualTo(EventStatus.DRAFT);
        assertThat(service.obtenerVisible(Optional.of(admin), ID).status()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void publishedIsVisibleToEveryone() {
        Event e = eventWithPrices();
        e.marcarPublicado();
        assertThat(service.obtenerVisible(Optional.empty(), ID).status()).isEqualTo(EventStatus.PUBLISHED);
    }

    // ------------------------------------------------------------------ cancelación

    @Test
    void cancelPublishedClosesInventoryAndNotifiesOrders() {
        Event e = eventWithPrices();
        e.marcarPublicado();

        EventResponse res = service.cancel(ID, "Cancelado por el organizador");

        assertThat(res.status()).isEqualTo(EventStatus.CANCELLED);
        verify(inventory).close(any());
        verify(orders).notifyEventCancelled(any(), eq("Cancelado por el organizador"));
    }

    @Test
    void cancelDraftDoesNotCallOtherServices() {
        eventWithPrices();
        assertThat(service.cancel(ID, null).status()).isEqualTo(EventStatus.CANCELLED);
        verify(inventory, never()).close(any());
        verify(orders, never()).notifyEventCancelled(any(), any());
    }

    @Test
    void cancelToleratesInventoryWithoutThatEvent() {
        Event e = eventWithPrices();
        e.marcarPublicado();
        doThrow(new PlatformException(ErrorCode.NOT_FOUND, "sin inventario")).when(inventory).close(any());

        assertThat(service.cancel(ID, "x").status()).isEqualTo(EventStatus.CANCELLED);
        verify(orders).notifyEventCancelled(any(), eq("x"));
    }

    @Test
    void cancelPropagatesOrderServiceFailure() {
        Event e = eventWithPrices();
        e.marcarPublicado();
        doThrow(new PlatformException(ErrorCode.UPSTREAM_UNAVAILABLE, "down")).when(orders)
                .notifyEventCancelled(any(), any());

        assertThatThrownBy(() -> service.cancel(ID, "x")).isInstanceOfSatisfying(PlatformException.class,
                ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
    }

    @Test
    void cancelFinishedOrAlreadyCancelledIs409() {
        Event e = eventWithPrices();
        e.cancel();
        assertThatThrownBy(() -> service.cancel(ID, "x")).isInstanceOfSatisfying(PlatformException.class,
                ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        verify(orders, never()).notifyEventCancelled(any(), any());
    }

    // ------------------------------------------------------------------ interno

    @Test
    void saleInfoOfPublishedEventIsPurchasableWithPricesPerSector() {
        Event e = eventWithPrices();
        e.marcarPublicado();

        SaleInfoResponse info = service.saleInfo(ID);

        assertThat(info.purchasable()).isTrue();
        assertThat(info.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(info.organizerId()).isEqualTo(owner.userId());
        assertThat(info.sectors()).hasSize(2);
        assertThat(info.sectors()).allMatch(s -> s.name() != null && !s.name().isBlank());
    }

    @Test
    void saleInfoIsNotPurchasableOutsideSale() {
        Event e = eventWithPrices();
        assertThat(service.saleInfo(ID).purchasable()).isFalse(); // DRAFT
        e.marcarPublicado();
        e.suspend();
        assertThat(service.saleInfo(ID).purchasable()).isFalse(); // SUSPENDED
        assertThat(service.saleInfo(ID).status()).isEqualTo(EventStatus.SUSPENDED);
    }

    @Test
    void inventoryCanMarkSoldOutAndReopen() {
        Event e = eventWithPrices();
        e.marcarPublicado();
        assertThat(service.applyInventoryStatus(ID, EventStatus.SOLD_OUT).status()).isEqualTo(EventStatus.SOLD_OUT);
        assertThat(service.applyInventoryStatus(ID, EventStatus.PUBLISHED).status()).isEqualTo(EventStatus.PUBLISHED);
    }
}
