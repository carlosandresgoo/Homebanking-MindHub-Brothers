package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByClientOrderByCreatedAtDescIdDesc(Client client, Pageable pageable);

    long countByClientAndReadAtIsNull(Client client);

    Optional<Notification> findByIdAndClient(Long id, Client client);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.client = :client and n.readAt is null")
    int markAllRead(Client client, LocalDateTime now);
}
