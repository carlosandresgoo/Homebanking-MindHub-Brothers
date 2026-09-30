package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByClientOrderByAliasAsc(Client client);

    Optional<Contact> findByIdAndClient(Long id, Client client);

    long countByClient(Client client);

    boolean existsByClientAndAccountNumber(Client client, String accountNumber);

    boolean existsByClientAndAccountNumberAndTrustedAtIsNotNull(Client client, String accountNumber);

    /** When 2FA is turned off, the trust it confirmed goes with it. */
    @Modifying
    @Query("update Contact c set c.trustedAt = null where c.client = :client")
    int untrustAll(Client client);

    boolean existsByClientAndAliasIgnoreCase(Client client, String alias);

    boolean existsByClientAndAliasIgnoreCaseAndIdNot(Client client, String alias, Long id);
}
