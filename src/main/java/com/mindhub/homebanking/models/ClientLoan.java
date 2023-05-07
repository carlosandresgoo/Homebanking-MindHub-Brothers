package com.mindhub.homebanking.models;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Entity
public class ClientLoan {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private long id;
    private double amount;
    private int payments;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client")
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "loan")
    private Loan loan;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "loanPayment")
    private LoanPayment loanPayment;

    private boolean setPaidOff;


    public ClientLoan() {
    }

    public ClientLoan(double amount, int payments, boolean setPaidOff) {
        this.amount = amount;
        this.payments = payments;
        this.setPaidOff = setPaidOff;
    }

    public ClientLoan(double v, int payments) {
    }

    public long getId() {
        return id;
    }


    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public int getPayments() {
        return payments;
    }

    public void setPayments(int payments) {
        this.payments = payments;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public LoanPayment getLoanPayment() {
        return loanPayment;
    }

    public void setLoanPayment(LoanPayment loanPayment) {
        this.loanPayment = loanPayment;
    }

    public Loan getLoan() {
        return loan;
    }

    public void setLoan(Loan loan) {
        this.loan = loan;
    }
    public void setPaidOff(boolean paidOff) {
        this.setPaidOff = paidOff;
    }

    public boolean isSetPaidOff() {
        return setPaidOff;
    }

    public void setSetPaidOff(boolean setPaidOff) {
        this.setPaidOff = setPaidOff;
    }
}
