package com.mindhub.homebanking.Controllers;

import com.mindhub.homebanking.dto.LoanApplicationDTO;
import com.mindhub.homebanking.dto.LoanDTO;
import com.mindhub.homebanking.models.*;
import com.mindhub.homebanking.repositories.AccountRepository;
import com.mindhub.homebanking.repositories.ClientLoanRepository;
import com.mindhub.homebanking.repositories.ClientRepository;
import com.mindhub.homebanking.repositories.LoanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RestController
public class LoanController {
    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private ClientRepository repository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private ClientLoanRepository clientLoanRepository;

    @RequestMapping("/api/loans")
    public List<LoanDTO> getLoans() {
        return loanRepository.findAll().stream().map(loan -> new LoanDTO(loan)).collect(toList());
    }

    @Transactional
    @PostMapping("/api/loans")
    public ResponseEntity<Object> createLoan (@RequestBody LoanApplicationDTO loanApplicationDTO, Authentication authentication){

        Client clientAuthenticated = repository.findByEmail(authentication.getName());

        Loan loan = loanRepository.findById(loanApplicationDTO.getId()).orElse(null);

        Account accountReceiver = accountRepository.findByNumber(loanApplicationDTO.getAccountNumber().toUpperCase());

        if (loan == null){
            return new ResponseEntity<>("Loan doesn't exist", HttpStatus.FORBIDDEN);
        }
        if (!(loan.getName().equalsIgnoreCase("MORTGAGE") || loan.getName().equalsIgnoreCase("PERSONAL") || loan.getName().equalsIgnoreCase("AUTOMOTIVE"))){
            return new ResponseEntity<>("Invalid loan ", HttpStatus.FORBIDDEN);
        }
        if (accountReceiver == null){
            return new ResponseEntity<>("Account doesn't exist", HttpStatus.FORBIDDEN);
        }
        if (String.valueOf(loanApplicationDTO.getAmount()).isBlank()){
            return new ResponseEntity<>("Amount can't be blank", HttpStatus.FORBIDDEN);
        }
        if (String.valueOf(loanApplicationDTO.getPayments()).isBlank()){
            return new ResponseEntity<>("Payment can't be blank", HttpStatus.FORBIDDEN);
        }
        if (loanApplicationDTO.getAmount() < 1){
            return new ResponseEntity<>("Amount can't be negative", HttpStatus.FORBIDDEN);
        }
        if (loanApplicationDTO.getAmount() > loan.getMaxAmount()){
            return new ResponseEntity<>("Amount can't be greater than max amount permitted", HttpStatus.FORBIDDEN);
        }
        if (!loan.getPayments().contains(loanApplicationDTO.getPayments())){
            return new ResponseEntity<>("That payment isn't permitted", HttpStatus.FORBIDDEN);
        }
        if (!clientAuthenticated.getAccounts().contains(accountReceiver)){
            return new ResponseEntity<>("The Account doesn't belong you", HttpStatus.FORBIDDEN);
        }

        // Validar que el cliente solo tenga un tipo de préstamo de cada uno de los tres tipos disponibles (mortgage, personal y automobile)
        for (ClientLoan existingLoan : clientAuthenticated.getClientLoans()) {
            if (existingLoan.getLoan().getName().equals(loan.getName())) {
                if (existingLoan.getPayments() == 0){
                    break; // permitir solicitud si el préstamo anterior fue pagado
                } else {
                    if (existingLoan.getPayments() == existingLoan.getPayments()) {
                        return new ResponseEntity<>("You already have an active loan of this type", HttpStatus.FORBIDDEN);
                    } else {
                        return new ResponseEntity<>("You still have an active loan of this type, please pay it off before applying for another one", HttpStatus.FORBIDDEN);
                    }
                }
            }
        }



        ClientLoan clientLoan = new ClientLoan(loanApplicationDTO.getAmount() + (loanApplicationDTO.getAmount() * 0.2), loanApplicationDTO.getPayments());
        clientLoanRepository.save(clientLoan);
        loan.addClientLoan(clientLoan);
        clientAuthenticated.addClientLoan(clientLoan);
        repository.save(clientAuthenticated);

        Transaction transaction = new Transaction(TransactionType.CREDIT, loanApplicationDTO.getAmount(), loan.getName() + " loan approved", LocalDateTime.now());
        //ADD BALANCE TO ACCOUNT RECEIVER
        accountReceiver.setBalance(accountReceiver.getBalance() + loanApplicationDTO.getAmount());
        accountReceiver.addTransaction(transaction);

        return new ResponseEntity<>("Loan approved successfully", HttpStatus.CREATED);
    }





}
