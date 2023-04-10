package com.mindhub.homebanking.dto;


import com.mindhub.homebanking.models.Client;


import java.util.Set;
import java.util.stream.Collectors;

public class ClientDTO {
    private String name;
    private String lastName;

    private long id;
    private String email;

    private Set<AccountDTO> accounts;

    public ClientDTO(Client client) {

        this.id = client.getId();

        this.name = client.getName();

        this.lastName = client.getLastName();

        this.email = client.getEmail();

        this.accounts = client.getAccounts().stream().map(account -> new AccountDTO(account)).collect(Collectors.toSet());

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public long getId() {
        return id;
    }


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<AccountDTO> getAccounts() {
        return accounts;
    }
}
