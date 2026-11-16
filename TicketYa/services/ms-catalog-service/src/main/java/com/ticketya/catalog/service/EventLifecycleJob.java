package com.ticketya.catalog.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticketya.catalog.entity.EventStatus;
import com.ticketya.catalog.repository.EventRepository;

/** Pasa a FINISHED los eventos activos cuya fecha ya pasó. Un UPDATE condicional es idempotente. */
@Component
public class EventLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(EventLifecycleJob.class);

    private final EventRepository events;
    private final Clock clock;

    public EventLifecycleJob(EventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${ticketya.jobs.finish-events-ms:60000}")
    @Transactional
    public void finishPastEvents() {
        int n = events.finishPastEvents(EventStatus.FINISHED,
                List.of(EventStatus.PUBLISHED, EventStatus.SOLD_OUT), OffsetDateTime.now(clock));
        if (n > 0) {
            log.info("events_finished count={}", n);
        }
    }
}
