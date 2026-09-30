package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A transfer the client asked to run later: once on {@code startDate}, or every week/month from it.
 * Occurrence {@code n} falls on {@code startDate + n} weeks/months (computed from the start, so a
 * monthly transfer on the 31st runs on the last day of shorter months without drifting). The
 * destination is fixed to an account number when it is scheduled.
 */
@Entity
public class ScheduledTransfer {

    public enum Frequency { ONCE, WEEKLY, MONTHLY }

    public enum Status { ACTIVE, PAUSED, FINISHED, CANCELLED }

    public enum Outcome { DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id")
    private Account source;

    @Column(nullable = false, length = 20)
    private String targetAccountNumber;

    /** Masked holder ("Lucía P.") captured when scheduled. */
    @Column(nullable = false, length = 60)
    private String targetHolder;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency;

    @Column(nullable = false)
    private LocalDate startDate;

    /** Index of the next occurrence; occurrences skipped while paused are counted too. */
    @Column(nullable = false)
    private int nextIndex;

    /** Date of the next occurrence; null once finished or cancelled. */
    private LocalDate nextRun;

    /** Executions so far, successful or not. */
    @Column(nullable = false)
    private int runs;

    /** Stop after this many executions; null = until cancelled (recurring only). */
    private Integer maxRuns;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    private LocalDateTime lastRunAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Outcome lastOutcome;

    @Column(length = 120)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected ScheduledTransfer() {
    }

    public ScheduledTransfer(Client client, Account source, String targetAccountNumber, String targetHolder,
                             BigDecimal amount, String description, Frequency frequency, LocalDate startDate,
                             Integer maxRuns, LocalDateTime createdAt) {
        this.client = client;
        this.source = source;
        this.targetAccountNumber = targetAccountNumber;
        this.targetHolder = targetHolder;
        this.amount = amount;
        this.description = description;
        this.frequency = frequency;
        this.startDate = startDate;
        this.maxRuns = frequency == Frequency.ONCE ? Integer.valueOf(1) : maxRuns;
        this.createdAt = createdAt;
        this.status = Status.ACTIVE;
        this.nextIndex = 0;
        this.nextRun = startDate;
    }

    public LocalDate occurrence(int n) {
        return switch (frequency) {
            case ONCE -> startDate;
            case WEEKLY -> startDate.plusWeeks(n);
            case MONTHLY -> startDate.plusMonths(n);
        };
    }

    public boolean isDue(LocalDate today) {
        return status == Status.ACTIVE && nextRun != null && !nextRun.isAfter(today);
    }

    /** One execution happened (or failed): records it and moves to the next occurrence, or finishes. */
    public void recordRun(LocalDateTime when, Outcome outcome, String error) {
        runs++;
        lastRunAt = when;
        lastOutcome = outcome;
        lastError = error;
        nextIndex++;
        if (frequency == Frequency.ONCE || (maxRuns != null && runs >= maxRuns)) {
            status = Status.FINISHED;
            nextRun = null;
        } else {
            nextRun = occurrence(nextIndex);
        }
    }

    /** Only recurring ones (a one-off is simply cancelled). */
    public void pause() {
        if (frequency == Frequency.ONCE) {
            throw new IllegalStateException("One-off transfers cannot be paused");
        }
        if (status == Status.ACTIVE) {
            status = Status.PAUSED;
        }
    }

    /** Occurrences that fell while paused are skipped, not run late. */
    public void resume(LocalDate today) {
        if (status != Status.PAUSED) {
            return;
        }
        status = Status.ACTIVE;
        while (!occurrence(nextIndex).isAfter(today)) {
            nextIndex++;
        }
        nextRun = occurrence(nextIndex);
    }

    public void cancel() {
        if (status == Status.ACTIVE || status == Status.PAUSED) {
            status = Status.CANCELLED;
            nextRun = null;
        }
    }

    public boolean isOpen() {
        return status == Status.ACTIVE || status == Status.PAUSED;
    }

    public Long getId() {
        return id;
    }

    public Client getClient() {
        return client;
    }

    public Account getSource() {
        return source;
    }

    public String getTargetAccountNumber() {
        return targetAccountNumber;
    }

    public String getTargetHolder() {
        return targetHolder;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getNextRun() {
        return nextRun;
    }

    public int getRuns() {
        return runs;
    }

    public Integer getMaxRuns() {
        return maxRuns;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public Outcome getLastOutcome() {
        return lastOutcome;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** No relations, no amount. */
    @Override
    public String toString() {
        return "ScheduledTransfer{id=" + id + ", status=" + status + "}";
    }
}
