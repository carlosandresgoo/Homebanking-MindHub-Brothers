package com.mindhub.homebanking.services;

import com.mindhub.homebanking.dto.LoanApplicationDTO;
import com.mindhub.homebanking.dto.LoanDTO;
import com.mindhub.homebanking.models.Loan;

import java.util.List;

public interface LoanService {
    List<LoanDTO> getLoanDTO();
    Loan loanById(LoanApplicationDTO loanApplicationDTO);
    void saveLoan(Loan loan);

}
