package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByOwnerAndKey(String owner, String key);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :before")
    int deleteOlderThan(Instant before);
}
