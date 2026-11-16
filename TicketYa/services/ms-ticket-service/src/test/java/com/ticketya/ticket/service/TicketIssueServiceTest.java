package com.ticketya.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketya.ticket.AbstractPostgresTest;
import com.ticketya.ticket.dto.request.IssueTicketsRequest;
import com.ticketya.ticket.dto.response.TicketResponse;
import com.ticketya.ticket.entity.TicketStatus;
import com.ticketya.ticket.exception.TicketException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TicketIssueServiceTest extends AbstractPostgresTest {

    @Autowired TicketIssueService issueService;

    private int ticketsOfOrder(UUID orderId) {
        return jdbc.queryForObject("select count(*) from ticket where order_id = ?", Integer.class, orderId);
    }

    @Test
    void issuesOneValidTicketPerItemEachWithDistinctQr() {
        UUID order = uuid(), event = uuid(), customer = uuid(), sector = uuid();
        var req = issueRequest(order, event, customer,
                item(uuid(), sector, "Platea A-5"), item(uuid(), sector, "Platea A-6"), item(uuid(), sector, "Platea A-7"));

        var result = issueService.issue(req);

        assertThat(result.created()).isTrue();                          // => 201
        List<TicketResponse> tickets = result.body().tickets();
        assertThat(tickets).hasSize(3).allMatch(t -> t.status() == TicketStatus.VALID);
        assertThat(tickets.stream().map(TicketResponse::qrCode).collect(Collectors.toSet())).hasSize(3);
        assertThat(tickets).extracting(TicketResponse::seatLabel).containsExactly("Platea A-5", "Platea A-6", "Platea A-7");
        assertThat(ticketsOfOrder(order)).isEqualTo(3);
    }

    @Test
    void generalZoneTicketHasNoSeatId() {
        UUID order = uuid();
        var zoneItem = new IssueTicketsRequest.Item(uuid(), uuid(), null, "Campo");

        var result = issueService.issue(issueRequest(order, uuid(), uuid(), zoneItem));

        assertThat(result.body().tickets()).singleElement().satisfies(t -> {
            assertThat(t.seatId()).isNull();
            assertThat(t.seatLabel()).isEqualTo("Campo");
        });
    }

    @Test
    void retryReturnsTheSameTicketsWithoutCreatingDuplicates() {
        UUID order = uuid();
        var req = issueRequest(order, uuid(), uuid(), item(uuid(), uuid(), "A-1"), item(uuid(), uuid(), "A-2"));

        var first = issueService.issue(req);
        var retry = issueService.issue(req);

        assertThat(retry.created()).isFalse();                          // => 200
        assertThat(retry.body().tickets()).extracting(TicketResponse::ticketId)
                .containsExactlyElementsOf(first.body().tickets().stream().map(TicketResponse::ticketId).toList());
        assertThat(retry.body().tickets()).extracting(TicketResponse::qrCode)
                .containsExactlyElementsOf(first.body().tickets().stream().map(TicketResponse::qrCode).toList());
        assertThat(ticketsOfOrder(order)).isEqualTo(2);
    }

    @Test
    void mixedRequestIssuesOnlyTheMissingItems() {
        UUID order = uuid(), event = uuid(), customer = uuid();
        var already = item(uuid(), uuid(), "A-1");
        issueService.issue(issueRequest(order, event, customer, already));

        var result = issueService.issue(issueRequest(order, event, customer, already, item(uuid(), uuid(), "A-2")));

        assertThat(result.created()).isTrue();
        assertThat(result.body().tickets()).hasSize(2);
        assertThat(ticketsOfOrder(order)).isEqualTo(2);
    }

    // ---------- 2.3 idempotencia concurrente ----------

    @Test
    void concurrentRequestsForSameOrderItemsCreateOneTicketPerItem() throws Exception {
        UUID order = uuid(), event = uuid(), customer = uuid();
        var req = issueRequest(order, event, customer,
                item(uuid(), uuid(), "A-1"), item(uuid(), uuid(), "A-2"), item(uuid(), uuid(), "A-3"));
        Map<Set<UUID>, Boolean> distinctResponses = new ConcurrentHashMap<>();

        Map<String, Integer> result = ConcurrencyHelper.race(10, i -> {
            var res = issueService.issue(req);
            distinctResponses.put(res.body().tickets().stream().map(TicketResponse::ticketId).collect(Collectors.toSet()), true);
        });

        assertThat(result).containsOnlyKeys("OK").containsEntry("OK", 10);
        assertThat(ticketsOfOrder(order)).isEqualTo(3);                 // una sola entrada por ítem
        assertThat(distinctResponses).hasSize(1);                       // todos recibieron las mismas entradas
        assertThat(jdbc.queryForObject("select count(distinct qr_code) from ticket where order_id = ?", Integer.class, order))
                .isEqualTo(3);
    }

    @Test
    void duplicatedOrderItemIdInsideRequestIsRejected() {
        UUID sameItem = uuid();
        var req = issueRequest(uuid(), uuid(), uuid(), item(sameItem, uuid(), "A-1"), item(sameItem, uuid(), "A-2"));

        assertThatThrownBy(() -> issueService.issue(req))
                .isInstanceOf(TicketException.class).extracting("code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void qrCodesAreUniqueAcrossAllIssuedTickets() {
        UUID order = uuid();
        IssueTicketsRequest.Item[] items = new IssueTicketsRequest.Item[200];
        for (int i = 0; i < items.length; i++) {
            items[i] = item(uuid(), uuid(), "Campo " + i);
        }

        issueService.issue(issueRequest(order, uuid(), uuid(), items));

        assertThat(jdbc.queryForObject("select count(distinct qr_code) from ticket where order_id = ?", Integer.class, order))
                .isEqualTo(200);
    }
}
