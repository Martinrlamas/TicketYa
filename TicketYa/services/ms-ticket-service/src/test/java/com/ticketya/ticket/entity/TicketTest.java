package com.ticketya.ticket.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.ticket.exception.TicketException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 2.1 Transiciones de estado válidas e inválidas (dominio puro, sin Spring ni base). */
class TicketTest {

    private static Ticket validTicket() {
        return Ticket.issue(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Platea A-5", "qr", Instant.now());
    }

    @Test
    void issuedTicketIsValid() {
        assertThat(validTicket().getStatus()).isEqualTo(TicketStatus.VALID);
    }

    @Test
    void validCanBeRefundedOrCancelled() {
        Ticket a = validTicket();
        assertThat(a.invalidate(InvalidationReason.REFUNDED)).isTrue();
        assertThat(a.getStatus()).isEqualTo(TicketStatus.REFUNDED);

        Ticket b = validTicket();
        assertThat(b.invalidate(InvalidationReason.CANCELLED)).isTrue();
        assertThat(b.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }

    @Test
    void invalidatingTwiceWithSameReasonIsIdempotent() {
        Ticket t = validTicket();
        t.invalidate(InvalidationReason.REFUNDED);

        assertThat(t.invalidate(InvalidationReason.REFUNDED)).isFalse();   // sin cambios
        assertThat(t.getStatus()).isEqualTo(TicketStatus.REFUNDED);
    }

    @Test
    void usedTicketCannotBeInvalidated() {
        Ticket t = validTicket();
        t.marcarUsada(Instant.now());

        for (InvalidationReason reason : InvalidationReason.values()) {
            assertThatThrownBy(() -> t.invalidate(reason))
                    .isInstanceOf(TicketException.class)
                    .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
        }
        assertThat(t.getStatus()).isEqualTo(TicketStatus.USED);
    }

    @Test
    void alreadyInvalidatedTicketCannotChangeToAnotherTerminalState() {
        Ticket t = validTicket();
        t.invalidate(InvalidationReason.REFUNDED);

        assertThatThrownBy(() -> t.invalidate(InvalidationReason.CANCELLED))
                .isInstanceOf(TicketException.class)
                .extracting("code").isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    void onlyValidTicketCanBeMarkedUsed() {
        Instant now = Instant.parse("2026-11-07T20:00:00Z");
        Ticket ok = validTicket();
        ok.marcarUsada(now);
        assertThat(ok.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(ok.getUsedAt()).isEqualTo(now);

        Ticket refunded = validTicket();
        refunded.invalidate(InvalidationReason.REFUNDED);
        assertThatThrownBy(() -> refunded.marcarUsada(now)).isInstanceOf(TicketException.class);

        assertThatThrownBy(() -> ok.marcarUsada(now)).isInstanceOf(TicketException.class);   // ya USED
    }

    @Test
    void ownershipIsDecidedByCustomerId() {
        Ticket t = validTicket();
        assertThat(t.esDuenio(t.getCustomerId())).isTrue();
        assertThat(t.esDuenio(UUID.randomUUID())).isFalse();
    }
}
