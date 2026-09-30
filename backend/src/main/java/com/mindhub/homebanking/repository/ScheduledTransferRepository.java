package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.ScheduledTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, Long> {

    @Query("select s from ScheduledTransfer s join fetch s.source where s.client = :client "
            + "order by s.createdAt desc, s.id desc")
    List<ScheduledTransfer> findMine(@Param("client") Client client);

    Optional<ScheduledTransfer> findByIdAndClient(Long id, Client client);

    long countByClientAndStatusIn(Client client, Collection<ScheduledTransfer.Status> statuses);

    @Query("select s.id from ScheduledTransfer s where s.status = 'ACTIVE' and s.nextRun <= :today "
            + "order by s.nextRun, s.id")
    List<Long> findDueIds(@Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScheduledTransfer s where s.id = :id")
    Optional<ScheduledTransfer> findByIdForUpdate(@Param("id") Long id);
}
