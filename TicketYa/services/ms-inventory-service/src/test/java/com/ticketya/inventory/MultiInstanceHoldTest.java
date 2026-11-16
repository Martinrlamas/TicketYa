package com.ticketya.inventory;

import static com.ticketya.inventory.TestFixtures.init;
import static com.ticketya.inventory.TestFixtures.numbered;
import static com.ticketya.inventory.TestFixtures.seatHold;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketya.inventory.dto.request.InitInventoryRequest.RowInit;
import com.ticketya.inventory.exception.InventoryException;
import com.ticketya.inventory.service.HoldService;
import com.ticketya.inventory.service.InventoryInitService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 3.4 Multi-instancia: dos contextos Spring independientes (como dos pods) contra el mismo PostgreSQL
 * compiten por el mismo asiento. Como la garantía está en la base, solo uno gana.
 */
class MultiInstanceHoldTest extends AbstractPostgresTest {

    @Autowired InventoryInitService initService;
    @Autowired HoldService instanceA;

    @Test
    void twoInstancesCompetingForSameSeatYieldOneWinner() throws Exception {
        UUID event = uuid(), sector = uuid();
        initService.init(event, init(numbered(sector, "Platea", new RowInit("A", 10))));
        UUID seat = jdbc.queryForObject(
                "select id from event_seat where event_id = ? and seat_number = 3", UUID.class, event);

        try (ConfigurableApplicationContext second = new SpringApplicationBuilder(InventoryServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "inventory.expire-job.enabled=false")
                .run()) {
            HoldService instanceB = second.getBean(HoldService.class);

            int threads = 40;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads), go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                HoldService target = (i % 2 == 0) ? instanceA : instanceB;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        target.create(seatHold(event, uuid(), sector, seat));
                        return "OK";
                    } catch (InventoryException e) {
                        return e.getCode();
                    }
                }));
            }
            ready.await();
            go.countDown();
            Map<String, Integer> result = new TreeMap<>();
            for (Future<String> f : futures) {
                result.merge(f.get(), 1, Integer::sum);
            }
            pool.shutdownNow();

            assertThat(result).containsEntry("OK", 1).containsEntry("SEAT_UNAVAILABLE", threads - 1);
            assertThat(jdbc.queryForObject("select count(*) from hold where event_id = ?", Integer.class, event)).isEqualTo(1);
        }
    }
}
