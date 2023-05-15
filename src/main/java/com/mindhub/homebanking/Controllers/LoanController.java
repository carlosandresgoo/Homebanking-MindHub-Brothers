package com.mindhub.homebanking.Controllers;

import com.mindhub.homebanking.dto.LoanApplicationDTO;
import com.mindhub.homebanking.dto.LoanDTO;
import com.mindhub.homebanking.models.*;

import com.mindhub.homebanking.repositories.AccountRepository;
import com.mindhub.homebanking.services.*;
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
    ClientService clientService;
    @Autowired
    AccountService accountService;
    @Autowired
    TransactionService transactionService;
    @Autowired
    LoanService loanService;
    @Autowired
    ClientLoanService clientLoanService;
    @Autowired
    private AccountRepository accountRepository;

    @RequestMapping("/api/loans")
    public List<LoanDTO> getLoans() {
        return loanService.getLoanDTO();
    }

    @Transactional
    @PostMapping("/api/loans")
    public ResponseEntity<Object> createLoan (@RequestBody LoanApplicationDTO loanApplicationDTO, Authentication authentication){

        Client clientAuthenticated = clientService.findByEmail(authentication.getName());

        Loan loan = loanService.loanById(loanApplicationDTO);

        Account accountReceiver = accountService.findByNumber(loanApplicationDTO.getAccountNumber().toUpperCase());

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
                    }
                }
            }
        }

        ClientLoan clientLoan = new ClientLoan(loanApplicationDTO.getAmount() + (loanApplicationDTO.getAmount() * 0.2), loanApplicationDTO.getPayments(), loanApplicationDTO.getAmount());
        clientLoanService.saveClientLoan(clientLoan);
        loan.addClientLoan(clientLoan);
        clientAuthenticated.addClientLoan(clientLoan);
        clientService.saveClient(clientAuthenticated);

        Transaction transaction = new Transaction(TransactionType.CREDIT, loanApplicationDTO.getAmount(), loan.getName() + " loan approved", LocalDateTime.now(),true, accountReceiver.getBalance());
        //ADD BALANCE TO ACCOUNT RECEIVER
        accountReceiver.setBalance(accountReceiver.getBalance() + loanApplicationDTO.getAmount());
        accountReceiver.addTransaction(transaction);


        return new ResponseEntity<>("Loan approved successfully", HttpStatus.CREATED);
    }
    @Transactional
    @PostMapping("/api/current/loans")
    public ResponseEntity<Object> payLoan(Authentication authentication , @RequestParam Long idLoan , @RequestParam String account, @RequestParam Double amount) {

        Client client = clientService.getClientAuthenticated(authentication);
        ClientLoan clientLoan = clientLoanService.getClientLoan(idLoan);
        Account accountAuthenticated = accountService.findByNumber(account);
        String description = "Pay " + clientLoan.getLoan().getName() + " loan";
//      id parameter
        if( clientLoan == null ){
            return new ResponseEntity<>("This loan doesn't exist", HttpStatus.FORBIDDEN);
        } else if( client == null){
            return new ResponseEntity<>("You are not registered as a client", HttpStatus.FORBIDDEN);}
//        account parameter
        if ( account.isBlank() ){
            return new ResponseEntity<>("PLease enter an account", HttpStatus.FORBIDDEN);
        } else if ( client.getAccounts().stream().filter(accounts -> accounts.getNumber().equalsIgnoreCase(account)).collect(toList()).size() == 0 ){
            return new ResponseEntity<>("This account is not yours.", HttpStatus.FORBIDDEN);}
//      amount parameter
        if ( amount < 1 ){
            return new ResponseEntity<>("PLease enter an amount bigger than 0", HttpStatus.FORBIDDEN);
        }  else if ( accountAuthenticated.getBalance() < amount ){
            return new ResponseEntity<>("Insufficient balance in your account " + accountAuthenticated.getNumber(), HttpStatus.FORBIDDEN);}

        Transaction newTransaction = new Transaction(TransactionType.DEBIT, amount, description , LocalDateTime.now(),true, accountAuthenticated.getBalance());
        accountAuthenticated.addTransaction(newTransaction);
        transactionService.saveTransaction(newTransaction);

        accountAuthenticated.setBalance( accountAuthenticated.getBalance() - amount );
        clientLoan.setFinalAmount( clientLoan.getFinalAmount() - amount);

        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}





