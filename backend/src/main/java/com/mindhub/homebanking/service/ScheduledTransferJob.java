package com.mindhub.homebanking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Runs the scheduled transfers due today, every morning, and once at start-up to catch up. */
@Component
public class ScheduledTransferJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferJob.class);

    private final ScheduledTransferService service;
    private final Clock clock;

    public ScheduledTransferJob(ScheduledTransferService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "${app.banking.scheduled-transfers.cron}", zone = "${app.banking.zone}")
    public void run() {
        int done = service.runDue(LocalDate.now(clock));
        if (done > 0) {
            log.info("Made {} scheduled transfer(s)", done);
        }
    }
}
