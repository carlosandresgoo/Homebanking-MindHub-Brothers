package com.mindhub.homebanking.models;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
public class LoanPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private LocalDateTime paymentDate;
    private double paymentAmount;
    private boolean paid;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "clientLoan")
    private ClientLoan clientLoan;

    public LoanPayment() {
    }

    public LoanPayment(double paymentAmount, ClientLoan clientLoan) {
        this.paymentAmount = paymentAmount;
        this.clientLoan = clientLoan;
        this.paid = false;
        this.paymentDate = LocalDateTime.now();
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

    public ClientLoan getClientLoan() {
        return clientLoan;
    }

    public void setClientLoan(ClientLoan clientLoan) {
        this.clientLoan = clientLoan;
    }


}
