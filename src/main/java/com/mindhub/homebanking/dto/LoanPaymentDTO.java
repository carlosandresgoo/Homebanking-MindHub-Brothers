package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.models.Loan;
import com.mindhub.homebanking.models.LoanPayment;

import java.time.LocalDateTime;

public class LoanPaymentDTO {
    private Long id;
    private LocalDateTime paymentDate;
    private double amount;


    public LoanPaymentDTO(LoanPayment loanPayment){
        this.id = loanPayment.getId();
        this.paymentDate = loanPayment.getPaymentDate();
        this.amount = loanPayment.getPaymentAmount();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public double getAmount() {
        return amount;
    }


}
