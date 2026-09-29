package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByNumber(String number);

    long countByClientAndActiveTrue(Client client);

    List<Account> findByClientEmailIgnoreCaseAndActiveTrueOrderByIdAsc(String email);

    /** Account with its owner and movements, for the detail view. */
    @EntityGraph(attributePaths = {"client", "transactions"})
    Optional<Account> findWithTransactionsById(Long id);

    @EntityGraph(attributePaths = "client")
    Optional<Account> findWithClientById(Long id);
}
