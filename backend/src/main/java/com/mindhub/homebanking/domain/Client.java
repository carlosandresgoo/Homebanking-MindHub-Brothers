package com.mindhub.homebanking.domain;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
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

    /** false = blocked by an administrator: cannot sign in until unblocked. */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Consecutive failed logins since the last successful one. */
    @Column(nullable = false)
    private int failedLoginAttempts;

    /** Temporary lock after too many failed logins; null or in the past = not locked. */
    private Instant lockedUntil;

    /** Encrypted TOTP secret: pending while {@code totpEnabled} is false, active once confirmed. */
    private String totpSecret;

    @Column(nullable = false)
    private boolean totpEnabled;

    @Embedded
    private AlertPreferences alerts = new AlertPreferences();

    /** Time step of the last accepted code: a code can be used only once. */
    private Long totpLastStep;

    protected Client() {
    }

    /** Stores a new (not yet confirmed) encrypted secret. */
    public void startTwoFactorEnrollment(String encryptedSecret) {
        this.totpSecret = encryptedSecret;
        this.totpEnabled = false;
        this.totpLastStep = null;
    }

    public void enableTwoFactor(long acceptedStep) {
        this.totpEnabled = true;
        this.totpLastStep = acceptedStep;
    }

    public void disableTwoFactor() {
        this.totpSecret = null;
        this.totpEnabled = false;
        this.totpLastStep = null;
    }

    public void registerSecondFactorStep(long acceptedStep) {
        this.totpLastStep = acceptedStep;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public boolean isTwoFactorEnabled() {
        return totpEnabled;
    }

    public Long getTotpLastStep() {
        return totpLastStep;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Counts a failed login and locks the account for {@code lockFor} when {@code maxAttempts} is reached. */
    public void registerFailedLogin(Instant now, int maxAttempts, Duration lockFor) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockFor);
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void block() {
        this.enabled = false;
    }

    /** Also clears any temporary lock. */
    public void unblock() {
        this.enabled = true;
        registerSuccessfulLogin();
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

    public AlertPreferences getAlerts() {
        return alerts;
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

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
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
