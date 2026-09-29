package com.mindhub.homebanking.mapper;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.dto.ClientDTO;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

    public ClientDTO toDto(Client client) {
        return new ClientDTO(
                client.getId(),
                client.getName(),
                client.getLastName(),
                client.getEmail(),
                client.getRole(),
                client.getAccounts().stream().map(this::toDto).toList());
    }

    public AccountDTO toDto(Account account) {
        return new AccountDTO(account.getId(), account.getNumber(), account.getCreationDate(), account.getBalance());
    }
}
