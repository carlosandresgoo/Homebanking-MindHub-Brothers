package com.mindhub.homebanking.Controllers;

import com.mindhub.homebanking.dto.LoanDTO;
import com.mindhub.homebanking.dto.LoanPaymentDTO;
import com.mindhub.homebanking.models.*;
import com.mindhub.homebanking.repositories.AccountRepository;
import com.mindhub.homebanking.repositories.ClientLoanRepository;
import com.mindhub.homebanking.repositories.ClientRepository;
import com.mindhub.homebanking.repositories.LoanPaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RestController
public class LoanPaymentController {
    @Autowired
    private  LoanPaymentRepository loanPaymentRepository;
    @Autowired
    private ClientRepository repository;
    @Autowired
    private ClientLoanRepository clientLoanRepository;
    @Autowired
    private AccountRepository accountRepository;

    @RequestMapping("/api/loanPayments")
    public List<LoanPaymentDTO> getLoanPayment() {
        return loanPaymentRepository.findAll().stream().map(loanPayment -> new LoanPaymentDTO(loanPayment)).collect(toList());
    }



    @PostMapping("/api/loan-payments")
    public ResponseEntity<Object> LoanPayment(@RequestBody LoanPaymentDTO loanPaymentDTO, Authentication authentication) {

        Client clientAuthenticated = repository.findByEmail(authentication.getName());
        ClientLoan clientLoan = clientLoanRepository.findById(loanPaymentDTO.getId()).orElse(null);

        if (clientLoan == null) {
            return new ResponseEntity<>("Client loan doesn't exist", HttpStatus.FORBIDDEN);
        }
        if (!clientAuthenticated.equals(clientLoan.getClient())) {
            return new ResponseEntity<>("Client loan doesn't belong to you", HttpStatus.FORBIDDEN);
        }
        if (loanPaymentDTO.getAmount() <= 0) {
            return new ResponseEntity<>("Amount should be greater than 0", HttpStatus.FORBIDDEN);
        }
        if (loanPaymentDTO.getAmount() > clientLoan.getAmount()) {
            return new ResponseEntity<>("Amount can't be greater than the remaining balance", HttpStatus.FORBIDDEN);
        }

        Account account = accountRepository.findByNumber(loanPaymentDTO.get());

        if (account == null) {
            return new ResponseEntity<>("Account doesn't exist", HttpStatus.FORBIDDEN);
        }
        if (!clientAuthenticated.getAccounts().contains(account)) {
            return new ResponseEntity<>("Account doesn't belong to you", HttpStatus.FORBIDDEN);
        }

        LoanPayment loanPayment = new LoanPayment(clientLoan, loanPaymentDTO.getAmount(), LocalDateTime.now());
        loanPaymentRepository.save(loanPayment);

        Transaction transaction = new Transaction(TransactionType.DEBIT, loanPaymentDTO.getAmount(), "Loan payment for " + clientLoan.getLoan().getName() + " loan", LocalDateTime.now());
        account.setBalance(account.getBalance() - loanPaymentDTO.getAmount());
        account.addTransaction(transaction);
        accountRepository.save(account);

        clientLoan.setPayments(clientLoan.getPayments() + 1);
        if (clientLoan.getPayments() == clientLoan.getLoan().getPayments().size()) {
            clientLoan.setPaidOff(true);
        }
        clientLoanRepository.save(clientLoan);

        return new ResponseEntity<>("Loan payment made successfully", HttpStatus.OK);
    }
}
