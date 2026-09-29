package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @EntityGraph(attributePaths = "client")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** A new request invalidates any earlier link. */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.client = :client and t.usedAt is null")
    int invalidateAll(Client client, Instant now);
}
