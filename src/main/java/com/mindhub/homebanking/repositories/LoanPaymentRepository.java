package com.mindhub.homebanking.repositories;

import com.mindhub.homebanking.models.LoanPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanPaymentRepository extends JpaRepository<LoanPayment, Long> {

}
