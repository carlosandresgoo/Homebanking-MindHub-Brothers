package com.mindhub.homebanking.Controllers;

import com.mindhub.homebanking.models.Account;
import com.mindhub.homebanking.models.Client;
import com.mindhub.homebanking.models.Transaction;
import com.mindhub.homebanking.models.TransactionType;
import com.mindhub.homebanking.repositories.AccountRepository;
import com.mindhub.homebanking.repositories.ClientRepository;
import com.mindhub.homebanking.repositories.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.stream.Collectors;


@RestController
public class TransactionController {
    @Autowired
    private ClientRepository repository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    @Transactional
    @PostMapping("/api/clients/current/transactions")
    public ResponseEntity<Object> newTransaction (
            Authentication authentication , @RequestParam double amount, @RequestParam String description,
            @RequestParam String initialAccount, @RequestParam String destinateAccount) {

        Client client = repository.findByEmail(authentication.getName());
        Account accountAuthenticated = accountRepository.findByNumber(initialAccount.toUpperCase());
        Account destinateAccountAuthenticated = accountRepository.findByNumber(destinateAccount.toUpperCase());

//      Amount parameter.
        if ( String.valueOf(amount) == null ) {
            return new ResponseEntity<>("Please enter an amount.", HttpStatus.FORBIDDEN);
        } else if( amount < 1 ){
            return new ResponseEntity<>("Please enter an amount bigger than 0.", HttpStatus.FORBIDDEN);
        } else if ( accountAuthenticated.getBalance() < amount ){
            return new ResponseEntity<>("You don't have the founds for this transaction", HttpStatus.FORBIDDEN);
        }
//      Description parameter.
        if ( description.isBlank() ) {
            description = "Transaction to " + destinateAccount;
        }
//      initialAccount Parameter.
        if ( initialAccount.isBlank()){
            return new ResponseEntity<>("Please enter one of your accounts", HttpStatus.FORBIDDEN);
        } else if ( accountAuthenticated == null) {
            return new ResponseEntity<>("This account " + initialAccount + " doesn't exist", HttpStatus.FORBIDDEN);
        } else if ( client.getAccounts().stream().filter(account -> account.getNumber().equalsIgnoreCase(initialAccount)).collect(Collectors.toList()).size() == 0 ){
            return new ResponseEntity<>("This account is not yours.", HttpStatus.FORBIDDEN);
        }
//      destinateAccount Parameter.
        if ( destinateAccount.isBlank() ){
            return new ResponseEntity<>("Please enter an account that you want to transfer the money", HttpStatus.FORBIDDEN);
        } else if ( destinateAccountAuthenticated == null ){
            return new ResponseEntity<>("This account " + destinateAccount + " doesn't exist", HttpStatus.FORBIDDEN);
        } else if (destinateAccount.equalsIgnoreCase(initialAccount)) {
            return new ResponseEntity<>("You can't send money to the same account number", HttpStatus.FORBIDDEN);
        }
//      Añadir transacciones
        Transaction newTransaction = new Transaction(TransactionType.DEBIT, amount, description, LocalDateTime.now());
        accountAuthenticated.addTransaction(newTransaction);
        transactionRepository.save(newTransaction);

        Transaction newTransaction2 = new Transaction(TransactionType.CREDIT, amount, description, LocalDateTime.now());
        destinateAccountAuthenticated.addTransaction(newTransaction2);
        transactionRepository.save(newTransaction2);
//      Restar o sumar valores a los balances.
        accountAuthenticated.setBalance( accountAuthenticated.getBalance() - amount );
        destinateAccountAuthenticated.setBalance( destinateAccountAuthenticated.getBalance() + amount );

        return new ResponseEntity<>(HttpStatus.CREATED);
    };
}
