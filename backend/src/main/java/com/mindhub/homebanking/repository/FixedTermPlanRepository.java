package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.FixedTermPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FixedTermPlanRepository extends JpaRepository<FixedTermPlan, Long> {

    List<FixedTermPlan> findAllByOrderByTermDaysAsc();

    Optional<FixedTermPlan> findByTermDays(int termDays);
}
