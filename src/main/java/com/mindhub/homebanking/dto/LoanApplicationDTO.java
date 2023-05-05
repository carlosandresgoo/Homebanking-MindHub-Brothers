package com.mindhub.homebanking.dto;

public class LoanApplicationDTO {
    private long id;
    private double amount;
    private int payments;
    private String accountNumber;

    public LoanApplicationDTO(long id, double amount, int payments,String accountNumber){
        this.id = id;
        this.amount = amount;
        this.payments = payments;
        this.accountNumber = accountNumber;
    }

    public long getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public int getPayments() {
        return payments;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
}
