package com.mindhub.homebanking.mapper;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Transaction;
import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.dto.AccountDetailDTO;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.dto.TransactionDTO;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class ClientMapper {

    private final Clock clock;

    public ClientMapper(Clock clock) {
        this.clock = clock;
    }

    /** Closed accounts are not part of the client's view. */
    public ClientDTO toDto(Client client) {
        return new ClientDTO(
                client.getId(),
                client.getName(),
                client.getLastName(),
                client.getEmail(),
                client.getRole(),
                client.isEnabled(),
                client.isLocked(clock.instant()),
                client.isTwoFactorEnabled(),
                client.getAccounts().stream().filter(Account::isActive).map(this::toDto).toList());
    }

    public AccountDTO toDto(Account account) {
        return new AccountDTO(account.getId(), account.getNumber(), account.getCbu(), account.getAlias(),
                account.getCreationDate(), account.getBalance());
    }

    public AccountDetailDTO toDetailDto(Account account) {
        return new AccountDetailDTO(
                account.getId(),
                account.getNumber(),
                account.getCbu(),
                account.getAlias(),
                account.getCreationDate(),
                account.getBalance());
    }

    public TransactionDTO toDto(Transaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getType(),
                transaction.getCategory(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getDate(),
                transaction.getBalanceAfter());
    }
}
