package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Loads clients with their accounts in one query (avoids N+1). */
    @EntityGraph(attributePaths = "accounts")
    List<Client> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "accounts")
    Optional<Client> findWithAccountsById(Long id);

    @EntityGraph(attributePaths = "accounts")
    Optional<Client> findWithAccountsByEmailIgnoreCase(String email);
}
