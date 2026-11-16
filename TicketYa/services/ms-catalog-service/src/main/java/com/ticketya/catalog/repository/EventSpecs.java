package com.ticketya.catalog.repository;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.ticketya.catalog.entity.Event;
import com.ticketya.catalog.entity.EventParticipant;
import com.ticketya.catalog.entity.EventStatus;
import com.ticketya.catalog.entity.EventType;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Criterios de búsqueda; los que reciben null devuelven null y {@code Specification.allOf} los ignora. */
public final class EventSpecs {

    private static final char ESC = '\\';

    private EventSpecs() {
    }

    public static Specification<Event> publiclyVisible() {
        return (r, q, cb) -> r.get("status").in(EventStatus.PUBLISHED, EventStatus.SOLD_OUT);
    }

    public static Specification<Event> type(EventType t) {
        return t == null ? null : (r, q, cb) -> cb.equal(r.get("type"), t);
    }

    public static Specification<Event> organizer(UUID id) {
        return id == null ? null : (r, q, cb) -> cb.equal(r.get("organizerId"), id);
    }

    public static Specification<Event> city(String city) {
        return city == null || city.isBlank() ? null
                : (r, q, cb) -> cb.equal(cb.lower(r.join("venue").get("city")), city.trim().toLowerCase());
    }

    /** Fecha mínima inclusive (inicio del día en la zona dada). */
    public static Specification<Event> from(LocalDate d, ZoneId zone) {
        return d == null ? null : (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("startsAt"),
                d.atStartOfDay(zone).toOffsetDateTime());
    }

    /** Fecha máxima inclusive (hasta el fin del día en la zona dada). */
    public static Specification<Event> to(LocalDate d, ZoneId zone) {
        return d == null ? null : (r, q, cb) -> cb.lessThan(r.get("startsAt"),
                d.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
    }

    /** Participante por nombre, sin distinguir mayúsculas ni acentos. EXISTS evita duplicados sin DISTINCT. */
    public static Specification<Event> participant(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return (r, q, cb) -> participantMatches(r, q.subquery(Integer.class), cb, likePattern(name));
    }

    /** Texto libre: nombre, descripción o participante (AND con el resto de filtros). */
    public static Specification<Event> text(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return (r, q, cb) -> {
            String pattern = likePattern(text);
            return cb.or(
                    nameMatches(r, cb, pattern),
                    descriptionMatches(r, cb, pattern),
                    participantMatches(r, q.subquery(Integer.class), cb, pattern));
        };
    }

    /**
     * Orden por relevancia: 3*(nombre) + 2*(participante) + 1*(descripción), desempate por fecha.
     * No agrega predicados; solo ordena (en la consulta de conteo no aplica). Requiere Pageable sin Sort.
     */
    public static Specification<Event> relevanceOrder(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return (r, q, cb) -> {
            if (Long.class.equals(q.getResultType())) {
                return null; // consulta de conteo
            }
            String pattern = likePattern(text);
            Expression<Integer> nameScore = cb.<Integer>selectCase().when(nameMatches(r, cb, pattern), 3).otherwise(0);
            Expression<Integer> partScore = cb.<Integer>selectCase()
                    .when(participantMatches(r, q.subquery(Integer.class), cb, pattern), 2).otherwise(0);
            Expression<Integer> descScore = cb.<Integer>selectCase()
                    .when(descriptionMatches(r, cb, pattern), 1).otherwise(0);
            Expression<Integer> score = cb.sum(cb.sum(nameScore, partScore), descScore);
            q.orderBy(cb.desc(score), cb.asc(r.get("startsAt")), cb.asc(r.get("id")));
            return null;
        };
    }

    // ---------------------------------------------------------------- helpers

    private static Predicate nameMatches(Root<Event> r, CriteriaBuilder cb, String pattern) {
        return cb.like(unaccentLower(cb, r.get("name")), pattern, ESC);
    }

    private static Predicate descriptionMatches(Root<Event> r, CriteriaBuilder cb, String pattern) {
        return cb.like(unaccentLower(cb, r.get("description")), pattern, ESC);
    }

    private static Predicate participantMatches(Root<Event> r, Subquery<Integer> sub, CriteriaBuilder cb,
            String pattern) {
        Root<EventParticipant> p = sub.from(EventParticipant.class);
        sub.select(cb.literal(1)).where(
                cb.equal(p.get("event"), r),
                cb.like(unaccentLower(cb, p.get("name")), pattern, ESC));
        return cb.exists(sub);
    }

    private static Expression<String> unaccentLower(CriteriaBuilder cb, Expression<String> expr) {
        return cb.function("unaccent", String.class, cb.lower(expr));
    }

    /** %texto% en minúsculas y sin acentos, con %, _ y \ escapados. */
    static String likePattern(String raw) {
        String s = stripAccents(raw.trim()).toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + s + "%";
    }

    static String stripAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
