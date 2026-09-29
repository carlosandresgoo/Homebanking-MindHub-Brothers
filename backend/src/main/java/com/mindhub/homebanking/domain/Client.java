package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    /** Password hash with its encoder id prefix (e.g. {@code {bcrypt}...}); never the raw password. */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @OneToMany(mappedBy = "client")
    @OrderBy("id")
    private List<Account> accounts = new ArrayList<>();

    protected Client() {
    }

    public Client(String name, String lastName, String email, String passwordHash, Role role) {
        this.name = name;
        this.lastName = lastName;
        this.email = email;
        this.password = passwordHash;
        this.role = role;
    }

    /** @param passwordHash already encoded (never the raw password) */
    public void changePassword(String passwordHash) {
        this.password = passwordHash;
    }

    public void addAccount(Account account) {
        account.setClient(this);
        accounts.add(account);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    /** Excludes the password and the accounts relation (avoids recursion and leaking data into logs). */
    @Override
    public String toString() {
        return "Client{id=" + id + ", role=" + role + '}';
    }
}
