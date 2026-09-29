package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.domain.TransactionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** Total sent to accounts of other clients since {@code since} (transfers between own accounts excluded). */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.account.client = :client and t.category = :category and t.date >= :since
              and t.counterparty not in (select a.number from Account a where a.client = :client)
            """)
    BigDecimal sumSentToOthersSince(@Param("client") Client client, @Param("since") LocalDateTime since,
                                    @Param("category") TransactionCategory category);

    default BigDecimal sumSentToOthersSince(Client client, LocalDateTime since) {
        return sumSentToOthersSince(client, since, TransactionCategory.TRANSFER_OUT);
    }
}
