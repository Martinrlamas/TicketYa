package com.ticketya.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.ticket.AbstractPostgresTest;
import com.ticketya.ticket.dto.response.TicketResponse;
import com.ticketya.ticket.entity.InvalidationReason;
import com.ticketya.ticket.entity.TicketStatus;
import com.ticketya.ticket.exception.TicketException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TicketInvalidationServiceTest extends AbstractPostgresTest {

    @Autowired TicketIssueService issueService;
    @Autowired TicketInvalidationService invalidationService;

    private TicketResponse issueOne(UUID event) {
        return issueService.issue(issueRequest(uuid(), event, uuid(), item(uuid(), uuid(), "A-1")))
                .body().tickets().get(0);
    }

    private String statusOf(UUID ticketId) {
        return jdbc.queryForObject("select status from ticket where id = ?", String.class, ticketId);
    }

    @Test
    void refundedReasonMovesValidTicketToRefunded() {
        TicketResponse t = issueOne(uuid());

        TicketResponse res = invalidationService.invalidate(t.ticketId(), InvalidationReason.REFUNDED);

        assertThat(res.status()).isEqualTo(TicketStatus.REFUNDED);
        assertThat(statusOf(t.ticketId())).isEqualTo("REFUNDED");
    }

    @Test
    void cancelledReasonMovesValidTicketToCancelled() {
        TicketResponse t = issueOne(uuid());

        invalidationService.invalidate(t.ticketId(), InvalidationReason.CANCELLED);

        assertThat(statusOf(t.ticketId())).isEqualTo("CANCELLED");
    }

    @Test
    void repeatedInvalidationWithSameReasonIsIdempotent() {
        TicketResponse t = issueOne(uuid());
        invalidationService.invalidate(t.ticketId(), InvalidationReason.REFUNDED);

        TicketResponse again = invalidationService.invalidate(t.ticketId(), InvalidationReason.REFUNDED);

        assertThat(again.status()).isEqualTo(TicketStatus.REFUNDED);
    }

    @Test
    void usedTicketCannotBeInvalidated() {
        TicketResponse t = issueOne(uuid());
        jdbc.update("update ticket set status = 'USED', used_at = now() where id = ?", t.ticketId());

        assertThatThrownBy(() -> invalidationService.invalidate(t.ticketId(), InvalidationReason.REFUNDED))
                .isInstanceOf(TicketException.class)
                .satisfies(e -> {
                    assertThat(((TicketException) e).getCode()).isEqualTo("INVALID_STATE_TRANSITION");
                    assertThat(((TicketException) e).getStatus().value()).isEqualTo(409);
                });
        assertThat(statusOf(t.ticketId())).isEqualTo("USED");
    }

    @Test
    void unknownTicketIsNotFound() {
        assertThatThrownBy(() -> invalidationService.invalidate(uuid(), InvalidationReason.REFUNDED))
                .isInstanceOf(TicketException.class).extracting("code").isEqualTo("NOT_FOUND");
    }

    @Test
    void bulkInvalidationCancelsOnlyValidTicketsOfThatEvent() {
        UUID event = uuid(), otherEvent = uuid();
        TicketResponse valid1 = issueOne(event);
        TicketResponse valid2 = issueOne(event);
        TicketResponse used = issueOne(event);
        TicketResponse refunded = issueOne(event);
        TicketResponse elsewhere = issueOne(otherEvent);
        jdbc.update("update ticket set status = 'USED' where id = ?", used.ticketId());
        jdbc.update("update ticket set status = 'REFUNDED' where id = ?", refunded.ticketId());

        var res = invalidationService.invalidarPorEvento(event, InvalidationReason.CANCELLED);

        assertThat(res.eventId()).isEqualTo(event);
        assertThat(res.invalidated()).isEqualTo(2);
        assertThat(statusOf(valid1.ticketId())).isEqualTo("CANCELLED");
        assertThat(statusOf(valid2.ticketId())).isEqualTo("CANCELLED");
        assertThat(statusOf(used.ticketId())).isEqualTo("USED");               // intacta
        assertThat(statusOf(refunded.ticketId())).isEqualTo("REFUNDED");       // intacta
        assertThat(statusOf(elsewhere.ticketId())).isEqualTo("VALID");         // otro evento
    }

    @Test
    void bulkInvalidationIsIdempotentAndOnlyAcceptsCancelled() {
        UUID event = uuid();
        issueOne(event);
        invalidationService.invalidarPorEvento(event, InvalidationReason.CANCELLED);

        assertThat(invalidationService.invalidarPorEvento(event, InvalidationReason.CANCELLED).invalidated()).isZero();
        assertThatThrownBy(() -> invalidationService.invalidarPorEvento(event, InvalidationReason.REFUNDED))
                .isInstanceOf(TicketException.class).extracting("code").isEqualTo("VALIDATION_ERROR");
    }
}
