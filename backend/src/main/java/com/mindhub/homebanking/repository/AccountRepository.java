package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByNumber(String number);

    boolean existsByCbu(String cbu);

    boolean existsByAliasIgnoreCase(String alias);

    boolean existsByAliasIgnoreCaseAndIdNot(String alias, Long id);

    long countByClientAndActiveTrue(Client client);

    List<Account> findByClientEmailIgnoreCaseAndActiveTrueOrderByIdAsc(String email);

    /** Open and closed accounts. */
    List<Account> findByClient(Client client);

    @EntityGraph(attributePaths = "client")
    Optional<Account> findWithClientById(Long id);

    /**
     * Id only (no entity is loaded), so the account can then be read fresh under a row lock with
     * {@link #findByIdForUpdate} instead of reusing a possibly stale instance.
     */
    @Query("select a.id from Account a where upper(a.number) = upper(:number)")
    Optional<Long> findIdByNumber(String number);

    @Query("select a.id from Account a where a.cbu = :cbu")
    Optional<Long> findIdByCbu(String cbu);

    @Query("select a.id from Account a where lower(a.alias) = lower(:alias)")
    Optional<Long> findIdByAlias(String alias);

    /** Row lock for balance updates; callers lock several accounts in ascending id order (no deadlocks). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(Long id);
}
