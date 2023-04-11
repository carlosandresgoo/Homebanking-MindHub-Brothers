package com.mindhub.homebanking.models;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

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