package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.FixedTerm;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FixedTermRepository extends JpaRepository<FixedTerm, Long> {

    @EntityGraph(attributePaths = "account")
    List<FixedTerm> findByClientOrderByStatusAscMaturityDateAscIdAsc(Client client);

    Optional<FixedTerm> findByIdAndClient(Long id, Client client);

    /** Ids only: each one is then paid in its own transaction under a row lock. */
    @Query("select f.id from FixedTerm f where f.status = :status and f.maturityDate <= :today order by f.id")
    List<Long> findIdsByStatusDueBy(@Param("status") FixedTerm.Status status, @Param("today") LocalDate today);

    default List<Long> findDueIds(LocalDate today) {
        return findIdsByStatusDueBy(FixedTerm.Status.ACTIVE, today);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FixedTerm f where f.id = :id")
    Optional<FixedTerm> findByIdForUpdate(@Param("id") Long id);

    boolean existsByAccountAndStatus(Account account, FixedTerm.Status status);
}
