package com.mindhub.homebanking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindhub.homebanking.domain.IdempotencyRecord;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Makes a non-idempotent operation safe to retry with an {@code Idempotency-Key}:
 * <ul>
 *   <li>first request: runs the operation and stores its response in the same transaction;</li>
 *   <li>retry with the same key and body: returns the stored response without running it again;</li>
 *   <li>same key, different body or operation: 422;</li>
 *   <li>two concurrent requests with the same key: the unique constraint makes the second one roll back
 *       entirely (including its money movements) and answer 409.</li>
 * </ul>
 * Without a key the operation simply runs (the header is optional for API clients).
 */
@Service
public class IdempotencyService {

    public record Result<T>(T body, boolean replayed) {
    }

    static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public <T> Result<T> execute(String owner, String key, String operation, Object request, int status,
                                 Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return new Result<>(action.get(), false);
        }
        String requestHash = hash(operation + '\n' + toJson(request));
        var existing = repository.findByOwnerAndKey(owner, key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getOperation().equals(operation) || !record.getRequestHash().equals(requestHash)) {
                throw new BusinessRuleException("This Idempotency-Key was already used for a different request");
            }
            return new Result<>(fromJson(record.getResponseBody(), responseType), true);
        }

        T response = action.get();
        try {
            repository.saveAndFlush(new IdempotencyRecord(owner, key, operation, requestHash, status,
                    toJson(response), clock.instant()));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            throw new ConflictException("A request with this Idempotency-Key is already being processed");
        }
        return new Result<>(response, false);
    }

    /** Keys are only needed for retries shortly after the original request. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        repository.deleteOlderThan(clock.instant().minus(RETENTION));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize idempotent payload", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read stored idempotent response", e);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
