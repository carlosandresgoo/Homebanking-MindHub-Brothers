package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.models.Client;
import com.mindhub.homebanking.models.ClientLoan;
import com.mindhub.homebanking.models.Loan;

import java.util.stream.Collectors;

public class ClientLoanDTO {

    private long id;
    private long loanId;
    private String name;
    private double amount;
    private int payments;





    public ClientLoanDTO(ClientLoan clientLoan) {

        this.id = clientLoan.getId();

        this.loanId = clientLoan.getLoan().getId();

        this.name = clientLoan.getName();

        this.amount = clientLoan.getAmount();

        this.payments = clientLoan.getPayments();

    }

    public long getId() {
        return id;
    }

    public long getLoanId() {
        return loanId;
    }

    public String getName() {
        return name;
    }

    public double getAmount() {
        return amount;
    }

    public int getPayments() {
        return payments;
    }
}
