package com.mindhub.homebanking.models;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
public class LoanPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private LocalDateTime paymentDate;
    private double paymentAmount;
    private boolean paid;

    @OneToMany(mappedBy="loanPayment", fetch= FetchType.EAGER)
    private Set<ClientLoan> clientLoans = new HashSet<>();

    public LoanPayment() {
    }

    public LoanPayment(double paymentAmount, ClientLoan clientLoan) {
        this.paymentAmount = paymentAmount;
        this.paymentDate = LocalDateTime.now();
    }

    public LoanPayment(ClientLoan clientLoan, double amount, LocalDateTime now) {
    }

    public Long getId() {
        return id;
    }


    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public double getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(double paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public Set<ClientLoan> getClientLoans() {
        return clientLoans;
    }

    public void setClientLoans(Set<ClientLoan> clientLoans) {
        this.clientLoans = clientLoans;
    }
}
