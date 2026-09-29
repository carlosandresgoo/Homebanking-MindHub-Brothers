package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
