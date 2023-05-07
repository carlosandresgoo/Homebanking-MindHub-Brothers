package com.mindhub.homebanking.repositories;

import com.mindhub.homebanking.models.LoanPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource
public interface LoanPaymentRepository extends JpaRepository<LoanPayment, Long> {

}
