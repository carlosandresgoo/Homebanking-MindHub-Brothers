package com.mindhub.homebanking.models;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class Client {

    private String firtsName;
    private String lastName;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private long id;
    private String email;

    @OneToMany(mappedBy="client", fetch= FetchType.EAGER)
     private Set<Account> accounts = new HashSet<>();
    @OneToMany(mappedBy="client", fetch= FetchType.EAGER)
    private Set<ClientLoan> clientLoans = new HashSet<>();

    public Client() {
    }

    public Client(String firtsName, String lastName, String email) {
        this.firtsName = firtsName;
        this.lastName = lastName;
        this.email = email;
    }
    public void addAccount(Account account) {
        account.setClient(this);
        accounts.add(account);
    }
    public void addClientLoan(ClientLoan clientLoan) {
        clientLoan.setClient(this);
        clientLoans.add(clientLoan);
    }
    public List<Loan> getLoans() {
        return clientLoans.stream().map(clientLoan -> clientLoan.getLoan()).collect(Collectors.toList());
    }
    public String getFirtsName() {
        return firtsName;
    }


    public Set<Account> getAccounts() {
        return accounts;
    }

    public void setFirtsName(String firtsName) {
        this.firtsName = firtsName;
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

    public Set<ClientLoan> getClientLoans() {
        return clientLoans;
    }

    public void setClientLoans(Set<ClientLoan> clientLoans) {
        this.clientLoans = clientLoans;
    }

    public void setAccounts(Set<Account> accounts) {
        this.accounts = accounts;
    }

    @Override
    public String toString() {
        return "Client{" +
                "name='" + firtsName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", id=" + id +
                ", email='" + email + '\'' +
                ", accounts=" + accounts +
                '}';
    }
}