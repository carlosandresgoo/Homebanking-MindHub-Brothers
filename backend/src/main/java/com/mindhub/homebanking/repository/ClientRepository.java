package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmailIgnoreCase(String email);

    /**
     * Row lock on the client: serializes operations checked per client (daily transfer limit,
     * single-use second-factor codes). Lock order: client first, then its accounts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where lower(c.email) = lower(:email)")
    Optional<Client> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Loads clients with their accounts in one query (avoids N+1). */
    @EntityGraph(attributePaths = "accounts")
    List<Client> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "accounts")
    Optional<Client> findWithAccountsById(Long id);

    @EntityGraph(attributePaths = "accounts")
    Optional<Client> findWithAccountsByEmailIgnoreCase(String email);
}
