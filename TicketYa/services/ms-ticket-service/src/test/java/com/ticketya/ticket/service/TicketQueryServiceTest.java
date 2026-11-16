package com.ticketya.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.ticket.AbstractPostgresTest;
import com.ticketya.ticket.dto.response.TicketResponse;
import com.ticketya.ticket.entity.TicketStatus;
import com.ticketya.ticket.exception.TicketException;
import com.ticketya.platform.error.ApiException;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.4 Acceso: un cliente solo ve sus propias entradas. */
class TicketQueryServiceTest extends AbstractPostgresTest {

    @Autowired TicketIssueService issueService;
    @Autowired TicketQueryService queryService;

    private TicketResponse issueOne(UUID event, UUID customer, String label) {
        return issueService.issue(issueRequest(uuid(), event, customer, item(uuid(), uuid(), label)))
                .body().tickets().get(0);
    }

    @Test
    void customerListsOnlyHisOwnTickets() {
        UUID event = uuid(), alice = uuid(), bob = uuid();
        TicketResponse a1 = issueOne(event, alice, "A-1");
        TicketResponse a2 = issueOne(event, alice, "A-2");
        TicketResponse b1 = issueOne(event, bob, "B-1");

        var page = queryService.listarMios(new Identity(alice, Role.CUSTOMER), null, null, 0, 20);

        assertThat(page.content()).extracting(TicketResponse::ticketId)
                .containsExactlyInAnyOrder(a1.ticketId(), a2.ticketId())
                .doesNotContain(b1.ticketId());
        assertThat(page.content()).allSatisfy(t -> assertThat(t.qrCode()).isNotBlank());
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void customerCannotSeeSomeoneElsesTicket() {
        UUID event = uuid(), alice = uuid(), bob = uuid();
        TicketResponse bobsTicket = issueOne(event, bob, "B-1");

        assertThatThrownBy(() -> queryService.obtenerUno(new Identity(alice, Role.CUSTOMER), bobsTicket.ticketId()))
                .isInstanceOf(TicketException.class)
                .satisfies(e -> {
                    assertThat(((TicketException) e).getCode()).isEqualTo("NOT_FOUND");
                    assertThat(((TicketException) e).getStatus().value()).isEqualTo(404);
                });
    }

    @Test
    void customerSeesHisOwnTicketDetail() {
        UUID alice = uuid();
        TicketResponse mine = issueOne(uuid(), alice, "A-1");

        assertThat(queryService.obtenerUno(new Identity(alice, Role.CUSTOMER), mine.ticketId()).ticketId())
                .isEqualTo(mine.ticketId());
    }

    @Test
    void adminCanSeeAnyTicket() {
        TicketResponse t = issueOne(uuid(), uuid(), "A-1");

        assertThat(queryService.obtenerUno(new Identity(uuid(), Role.ADMIN), t.ticketId()).ticketId()).isEqualTo(t.ticketId());
    }

    @Test
    void unknownTicketIsNotFound() {
        assertThatThrownBy(() -> queryService.obtenerUno(new Identity(uuid(), Role.ADMIN), uuid()))
                .isInstanceOf(TicketException.class).extracting("code").isEqualTo("NOT_FOUND");
    }

    @Test
    void listIsOnlyForCustomersAndDetailNotForStaffOrOrganizers() {
        assertThatThrownBy(() -> queryService.listarMios(new Identity(uuid(), Role.ADMIN), null, null, 0, 20))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> queryService.obtenerUno(new Identity(uuid(), Role.ACCESS_STAFF), uuid()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FORBIDDEN");
    }

    @Test
    void filtersByEventAndStatusAndPaginates() {
        UUID eventA = uuid(), eventB = uuid(), alice = uuid();
        for (int i = 0; i < 5; i++) {
            issueOne(eventA, alice, "A-" + i);
        }
        TicketResponse other = issueOne(eventB, alice, "B-1");
        jdbc.update("update ticket set status = 'REFUNDED' where id = ?", other.ticketId());
        var caller = new Identity(alice, Role.CUSTOMER);

        var firstPage = queryService.listarMios(caller, eventA, null, 0, 2);
        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.totalPages()).isEqualTo(3);

        var refunded = queryService.listarMios(caller, null, TicketStatus.REFUNDED, 0, 20);
        assertThat(refunded.content()).extracting(TicketResponse::ticketId).containsExactly(other.ticketId());
    }

    @Test
    void pageSizeIsCappedAtOneHundredAndNegativePageIsRejected() {
        var caller = new Identity(uuid(), Role.CUSTOMER);

        assertThat(queryService.listarMios(caller, null, null, 0, 1000).size()).isEqualTo(100);
        assertThatThrownBy(() -> queryService.listarMios(caller, null, null, -1, 20))
                .isInstanceOf(TicketException.class).extracting("code").isEqualTo("VALIDATION_ERROR");
    }
}
