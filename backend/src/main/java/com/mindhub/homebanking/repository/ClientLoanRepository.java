package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.ClientLoan;
import com.mindhub.homebanking.domain.Loan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClientLoanRepository extends JpaRepository<ClientLoan, Long> {

    @EntityGraph(attributePaths = "loan")
    List<ClientLoan> findByClientEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    /** An unpaid loan of this product blocks a new application for it. */
    @Query("select count(cl) > 0 from ClientLoan cl "
            + "where cl.client = :client and cl.loan = :loan and cl.paymentsMade < cl.payments")
    boolean hasActiveLoan(Client client, Loan loan);

    /** Locked while an installment is paid, so two concurrent payments cannot pay the same one. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cl from ClientLoan cl join fetch cl.client join fetch cl.loan where cl.id = :id")
    Optional<ClientLoan> findByIdForUpdate(Long id);
}
