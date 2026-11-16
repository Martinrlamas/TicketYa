package com.ticketya.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticketya.catalog.dto.request.EventSearchCriteria;
import com.ticketya.catalog.dto.response.EventResponse;
import com.ticketya.platform.web.PageResponse;
import com.ticketya.catalog.mapper.EventMapper;
import com.ticketya.catalog.entity.Event;
import com.ticketya.catalog.entity.EventStatus;
import com.ticketya.catalog.entity.EventType;
import com.ticketya.catalog.entity.ParticipantRole;
import com.ticketya.catalog.entity.Sector;
import com.ticketya.catalog.entity.Venue;
import com.ticketya.catalog.service.EventSearchService;
import com.ticketya.platform.error.PlatformException;
import com.ticketya.platform.error.ErrorCode;

/** Tarea 3.3: búsqueda contra PostgreSQL real (Flyway + unaccent). Requiere Docker. */
@DataJpaTest
@ActiveProfiles("test") // sin el perfil local: no carga los datos de prueba
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class EventSearchIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), AR);
    static final UUID ORG = UUID.randomUUID();

    @Autowired EventRepository events;
    @Autowired VenueRepository venues;
    @Autowired SectorRepository sectors;
    @Autowired TestEntityManager em;
    @Autowired JdbcTemplate jdbc;

    EventSearchService search;
    Venue tandil;
    Venue azul;
    Sector tandilCampo;
    Sector azulCampo;

    @BeforeEach
    void setUp() {
        search = new EventSearchService(events, new EventMapper(), CLOCK);
        tandil = venues.save(Venue.create("Estadio", "Av. 1", "Tandil", new BigDecimal("-37.32"), new BigDecimal("-59.13")));
        azul = venues.save(Venue.create("Teatro", "Calle 2", "Azul", new BigDecimal("-36.77"), new BigDecimal("-59.85")));
        tandilCampo = sectors.save(Sector.general(tandil, "Campo", 1000));
        azulCampo = sectors.save(Sector.general(azul, "Butacas", 300));
    }

    // ------------------------------------------------------------------ helpers

    private OffsetDateTime at(int day, int hour) {
        return LocalDate.of(2026, 12, day).atTime(hour, 0).atZone(AR).toOffsetDateTime();
    }

    private Event event(String name, String description, EventType type, Venue venue, OffsetDateTime startsAt,
            String price, EventStatus status, String... participants) {
        Event e = Event.draft(ORG, venue, name, description, type, startsAt, CLOCK);
        for (String p : participants) {
            e.agregarParticipante(p, ParticipantRole.ARTIST);
        }
        e.reemplazarPrecios(Map.of(venue == tandil ? tandilCampo : azulCampo, new BigDecimal(price)));
        switch (status) {
            case PUBLISHED -> e.marcarPublicado();
            case SOLD_OUT -> { e.marcarPublicado(); e.soldOut(); }
            case SUSPENDED -> { e.marcarPublicado(); e.suspend(); }
            case CANCELLED -> e.cancel();
            default -> { }
        }
        Event saved = events.save(e);
        em.flush();
        return saved;
    }

    private EventSearchCriteria criteria(EventType type, String participant, String city, LocalDate from,
            LocalDate to, String q, String sort, String order, int page, int size) {
        return new EventSearchCriteria(type, null, participant, city, from, to, q, sort, order, page, size);
    }

    private EventSearchCriteria all() {
        return criteria(null, null, null, null, null, null, null, null, 0, 20);
    }

    private List<String> names(PageResponse<EventResponse> page) {
        return page.content().stream().map(EventResponse::name).toList();
    }

    private PageResponse<EventResponse> run(EventSearchCriteria c) {
        em.clear();
        return search.search(c);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void onlyPublishedAndSoldOutAreVisible() {
        event("Publicado", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED);
        event("Agotado", null, EventType.CONCERT, tandil, at(11, 21), "100", EventStatus.SOLD_OUT);
        event("Borrador", null, EventType.CONCERT, tandil, at(12, 21), "100", EventStatus.DRAFT);
        event("Suspendido", null, EventType.CONCERT, tandil, at(13, 21), "100", EventStatus.SUSPENDED);
        event("Cancelado", null, EventType.CONCERT, tandil, at(14, 21), "100", EventStatus.CANCELLED);

        assertThat(names(run(all()))).containsExactly("Publicado", "Agotado");
    }

    @Test
    void filtersByTypeAndCityCombined() {
        event("Rock Tandil", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED);
        event("Teatro Tandil", null, EventType.THEATER, tandil, at(11, 21), "100", EventStatus.PUBLISHED);
        event("Rock Azul", null, EventType.CONCERT, azul, at(12, 21), "100", EventStatus.PUBLISHED);

        assertThat(names(run(criteria(EventType.CONCERT, null, "Tandil", null, null, null, null, null, 0, 20))))
                .containsExactly("Rock Tandil");
        assertThat(names(run(criteria(null, null, "azul", null, null, null, null, null, 0, 20))))
                .containsExactly("Rock Azul");
    }

    @Test
    void participantMatchIgnoresCaseAndAccents() {
        event("Show A", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED, "Divididos");
        event("Show B", null, EventType.CONCERT, tandil, at(11, 21), "100", EventStatus.PUBLISHED, "Ágatha Trío");
        event("Show C", null, EventType.CONCERT, tandil, at(12, 21), "100", EventStatus.PUBLISHED, "Otros");

        assertThat(names(run(criteria(null, "DIVIDIDOS", null, null, null, null, null, null, 0, 20)))).containsExactly("Show A");
        assertThat(names(run(criteria(null, "agatha trio", null, null, null, null, null, null, 0, 20)))).containsExactly("Show B");
        assertThat(names(run(criteria(null, "Ágatha", null, null, null, null, null, null, 0, 20)))).containsExactly("Show B");
    }

    @Test
    void participantFilterDoesNotDuplicateEventsWithManyMatches() {
        event("Doble", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED, "Rock Uno", "Rock Dos");

        PageResponse<EventResponse> page = run(criteria(null, "rock", null, null, null, null, null, null, 0, 20));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).participants()).hasSize(2);
    }

    @Test
    void dateRangeIsInclusiveInTheConfiguredZone() {
        event("Antes", null, EventType.CONCERT, tandil, at(9, 23), "100", EventStatus.PUBLISHED);
        event("Primer día", null, EventType.CONCERT, tandil, at(10, 0), "100", EventStatus.PUBLISHED);
        event("Último día", null, EventType.CONCERT, tandil, at(12, 23), "100", EventStatus.PUBLISHED);
        event("Después", null, EventType.CONCERT, tandil, at(13, 0), "100", EventStatus.PUBLISHED);

        PageResponse<EventResponse> page = run(criteria(null, null, null, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 12), null, null, null, 0, 20));

        assertThat(names(page)).containsExactly("Primer día", "Último día");
    }

    @Test
    void sortsByMinPriceAscendingAndDescending() {
        event("Caro", null, EventType.CONCERT, tandil, at(10, 21), "300", EventStatus.PUBLISHED);
        event("Barato", null, EventType.CONCERT, tandil, at(11, 21), "100", EventStatus.PUBLISHED);
        event("Medio", null, EventType.CONCERT, tandil, at(12, 21), "200", EventStatus.PUBLISHED);

        assertThat(names(run(criteria(null, null, null, null, null, null, "price", "asc", 0, 20))))
                .containsExactly("Barato", "Medio", "Caro");
        assertThat(names(run(criteria(null, null, null, null, null, null, "price", "desc", 0, 20))))
                .containsExactly("Caro", "Medio", "Barato");
    }

    @Test
    void defaultSortIsDateAscendingAndCanBeReversed() {
        event("Tercero", null, EventType.CONCERT, tandil, at(12, 21), "100", EventStatus.PUBLISHED);
        event("Primero", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED);
        event("Segundo", null, EventType.CONCERT, tandil, at(11, 21), "100", EventStatus.PUBLISHED);

        assertThat(names(run(all()))).containsExactly("Primero", "Segundo", "Tercero");
        assertThat(names(run(criteria(null, null, null, null, null, null, "date", "desc", 0, 20))))
                .containsExactly("Tercero", "Segundo", "Primero");
    }

    @Test
    void relevancePutsNameMatchBeforeDescriptionOnlyMatch() {
        event("Festival general", "Una noche de rock", EventType.FESTIVAL, tandil, at(10, 21), "100", EventStatus.PUBLISHED);
        event("Rock en el parque", "Entrada libre", EventType.FESTIVAL, tandil, at(12, 21), "100", EventStatus.PUBLISHED);
        event("Jazz", "Sin coincidencia", EventType.FESTIVAL, tandil, at(11, 21), "100", EventStatus.PUBLISHED);

        PageResponse<EventResponse> page = run(criteria(null, null, null, null, null, "rock", "relevance", null, 0, 20));

        assertThat(names(page)).containsExactly("Rock en el parque", "Festival general");
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void relevanceCountsParticipantMatchBetweenNameAndDescription() {
        event("Solo descripción", "habrá rock", EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED);
        event("Con participante", "nada", EventType.CONCERT, tandil, at(11, 21), "100", EventStatus.PUBLISHED, "Rock Nacional");
        event("Con nombre", "nada", EventType.CONCERT, tandil, at(12, 21), "100", EventStatus.PUBLISHED);
        // el tercero no coincide con "rock" en nombre: se renombra vía SQL para no ensuciar el helper
        jdbc.update("update event set name = 'Rock total' where name = 'Con nombre'");

        PageResponse<EventResponse> page = run(criteria(null, null, null, null, null, "rock", "relevance", null, 0, 20));

        assertThat(names(page)).containsExactly("Rock total", "Con participante", "Solo descripción");
    }

    @Test
    void relevanceWithoutTextFallsBackToDateAscending() {
        event("B", null, EventType.CONCERT, tandil, at(11, 21), "100", EventStatus.PUBLISHED);
        event("A", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED);

        assertThat(names(run(criteria(null, null, null, null, null, null, "relevance", null, 0, 20))))
                .containsExactly("A", "B");
    }

    @Test
    void freeTextIgnoresAccentsAndCase() {
        event("Música Clásica", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED);

        assertThat(names(run(criteria(null, null, null, null, null, "MUSICA clasica", null, null, 0, 20))))
                .containsExactly("Música Clásica");
    }

    @Test
    void likeWildcardsInTheQueryAreTreatedAsLiterals() {
        event("Descuento 50% off", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED);
        event("Cincuenta", null, EventType.CONCERT, tandil, at(11, 21), "100", EventStatus.PUBLISHED);

        assertThat(names(run(criteria(null, null, null, null, null, "%", null, null, 0, 20))))
                .containsExactly("Descuento 50% off");
    }

    @Test
    void paginatesAndReportsTotals() {
        for (int i = 1; i <= 5; i++) {
            event("Evento " + i, null, EventType.CONCERT, tandil, at(10 + i, 21), "100", EventStatus.PUBLISHED);
        }

        PageResponse<EventResponse> first = run(criteria(null, null, null, null, null, null, null, null, 0, 2));
        PageResponse<EventResponse> last = run(criteria(null, null, null, null, null, null, null, null, 2, 2));

        assertThat(first.content()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(last.content()).hasSize(1);
        assertThat(names(first)).containsExactly("Evento 1", "Evento 2");
    }

    @Test
    void noMatchReturnsEmptyPage() {
        event("Show", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED);

        PageResponse<EventResponse> page = run(criteria(EventType.SPORT, null, null, null, null, null, null, null, 0, 20));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void oversizedPageOrInvalidSortIsValidationError() {
        for (Runnable bad : List.<Runnable>of(
                () -> criteria(null, null, null, null, null, null, null, null, 0, 500),
                () -> criteria(null, null, null, null, null, null, "color", null, 0, 20),
                () -> criteria(null, null, null, null, null, null, null, "up", 0, 20),
                () -> criteria(null, null, null, null, null, null, null, null, -1, 20))) {
            assertThatThrownBy(bad::run).isInstanceOfSatisfying(PlatformException.class,
                    ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
    }

    @Test
    void finishPastEventsOnlyAffectsActiveEventsInThePast() {
        Event past = event("Pasado", null, EventType.CONCERT, tandil, at(10, 21), "100", EventStatus.PUBLISHED);
        Event pastSoldOut = event("Pasado agotado", null, EventType.CONCERT, tandil, at(11, 21), "100", EventStatus.SOLD_OUT);
        Event pastDraft = event("Pasado borrador", null, EventType.CONCERT, tandil, at(12, 21), "100", EventStatus.DRAFT);
        Event future = event("Futuro", null, EventType.CONCERT, tandil, at(20, 21), "100", EventStatus.PUBLISHED);
        jdbc.update("update event set starts_at = now() - interval '1 day' where id in (?, ?, ?)",
                past.getId(), pastSoldOut.getId(), pastDraft.getId());
        jdbc.update("update event set starts_at = now() + interval '30 days' where id = ?", future.getId());

        int updated = events.finishPastEvents(EventStatus.FINISHED,
                List.of(EventStatus.PUBLISHED, EventStatus.SOLD_OUT), OffsetDateTime.now());
        em.clear();

        assertThat(updated).isEqualTo(2);
        assertThat(events.findById(past.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.FINISHED);
        assertThat(events.findById(pastSoldOut.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.FINISHED);
        assertThat(events.findById(pastDraft.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(events.findById(future.getId()).orElseThrow().getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(events.finishPastEvents(EventStatus.FINISHED,
                List.of(EventStatus.PUBLISHED, EventStatus.SOLD_OUT), OffsetDateTime.now())).isZero(); // idempotente
    }
}
