package com.mindhub.homebanking.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledTransferTest {

    private static ScheduledTransfer scheduled(ScheduledTransfer.Frequency frequency, LocalDate start, Integer maxRuns) {
        return new ScheduledTransfer(null, null, "VIN999", "Other C.", BigDecimal.TEN, null, frequency, start,
                maxRuns, LocalDateTime.now());
    }

    @Test
    void monthlyOccurrencesAreCountedFromTheStartSoTheyDoNotDrift() {
        ScheduledTransfer monthly = scheduled(ScheduledTransfer.Frequency.MONTHLY, LocalDate.of(2027, 1, 31), null);
        assertThat(monthly.occurrence(1)).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(monthly.occurrence(2)).isEqualTo(LocalDate.of(2027, 3, 31)); // not the 28th
        assertThat(monthly.occurrence(13)).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void eachRunMovesToTheNextOccurrenceUntilTheLimit() {
        LocalDate start = LocalDate.of(2027, 1, 4);
        ScheduledTransfer weekly = scheduled(ScheduledTransfer.Frequency.WEEKLY, start, 2);
        assertThat(weekly.isDue(start.minusDays(1))).isFalse();
        assertThat(weekly.isDue(start)).isTrue();

        weekly.recordRun(LocalDateTime.now(), ScheduledTransfer.Outcome.FAILED, "Sin saldo");
        assertThat(weekly.getNextRun()).isEqualTo(start.plusWeeks(1));
        assertThat(weekly.getStatus()).isEqualTo(ScheduledTransfer.Status.ACTIVE);
        assertThat(weekly.getLastError()).isEqualTo("Sin saldo");

        weekly.recordRun(LocalDateTime.now(), ScheduledTransfer.Outcome.DONE, null);
        assertThat(weekly.getStatus()).isEqualTo(ScheduledTransfer.Status.FINISHED);
        assertThat(weekly.getNextRun()).isNull();
        assertThat(weekly.getRuns()).isEqualTo(2);
    }

    @Test
    void aOneOffFinishesAfterItsOnlyRun() {
        ScheduledTransfer once = scheduled(ScheduledTransfer.Frequency.ONCE, LocalDate.of(2027, 1, 4), 5);
        assertThat(once.getMaxRuns()).isEqualTo(1);
        once.recordRun(LocalDateTime.now(), ScheduledTransfer.Outcome.DONE, null);
        assertThat(once.getStatus()).isEqualTo(ScheduledTransfer.Status.FINISHED);
    }

    @Test
    void occurrencesMissedWhilePausedAreSkippedAndNotCounted() {
        LocalDate start = LocalDate.of(2027, 1, 4);
        ScheduledTransfer weekly = scheduled(ScheduledTransfer.Frequency.WEEKLY, start, null);
        weekly.pause();
        assertThat(weekly.isDue(start)).isFalse();

        weekly.resume(start.plusDays(15)); // the 4th, 11th and 18th went by
        assertThat(weekly.getStatus()).isEqualTo(ScheduledTransfer.Status.ACTIVE);
        assertThat(weekly.getNextRun()).isEqualTo(start.plusWeeks(3));
        assertThat(weekly.getRuns()).isZero();

        weekly.cancel();
        assertThat(weekly.getStatus()).isEqualTo(ScheduledTransfer.Status.CANCELLED);
        assertThat(weekly.getNextRun()).isNull();
    }
}
