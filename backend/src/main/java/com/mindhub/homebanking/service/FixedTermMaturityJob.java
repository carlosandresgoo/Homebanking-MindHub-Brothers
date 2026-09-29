package com.mindhub.homebanking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/** Pays matured fixed terms every day, and once at start-up to catch up after downtime. */
@Component
public class FixedTermMaturityJob {

    private static final Logger log = LoggerFactory.getLogger(FixedTermMaturityJob.class);

    private final FixedTermService fixedTermService;
    private final Clock clock;

    public FixedTermMaturityJob(FixedTermService fixedTermService, Clock clock) {
        this.fixedTermService = fixedTermService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "${app.banking.fixed-terms.payout-cron}", zone = "${app.banking.zone}")
    public void run() {
        int paid = fixedTermService.payDue(LocalDate.now(clock));
        if (paid > 0) {
            log.info("Paid {} matured fixed term(s)", paid);
        }
    }
}
