package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Loan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findAllByOrderByIdAsc();

    Optional<Loan> findByCode(String code);
}
