package com.mindhub.homebanking.Controllers;
import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.models.Account;
import com.mindhub.homebanking.models.Client;
import com.mindhub.homebanking.repositories.AccountRepository;
import com.mindhub.homebanking.repositories.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;


import static java.util.stream.Collectors.toList;


@RestController
public class AccountController {

        @Autowired
        private AccountRepository accountRepository;
        @Autowired
        private ClientRepository repository;

        public String randomNumber(){
                String randomNumber;
                do {
                        int number = (int) (Math.random() * 899999 + 100000);
                        randomNumber = "VIN-" + number;
                } while (accountRepository.findByNumber(randomNumber) != null);
                return randomNumber;
        }

        @RequestMapping("/api/clients/current/accounts")
        public List<AccountDTO> getAccount(Authentication authentication) {
                return new ClientDTO(repository.findByEmail(authentication.getName())).getAccounts().stream().collect(toList());
        }

        @RequestMapping("/api/clients/current/accounts/{id}")
        public AccountDTO getAccount (@PathVariable Long id){
                return accountRepository.findById(id).map(account -> new AccountDTO(account)).orElse(null);
        }


        @PostMapping("/api/clients/current/accounts")
        public ResponseEntity<Object> createAccount(Authentication authentication) {

                Client client = repository.findByEmail(authentication.getName());
                if (client.getAccounts().size() >= 3) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body("Client already has the maximum number of accounts allowed.");
                }

                if(client == null) {
                        return new ResponseEntity<>("you can't create a accounts because you're not a client.", HttpStatus.NOT_FOUND);
                }

                String accountNumber = randomNumber();
                Account newAccount = new Account(accountNumber, LocalDateTime.now(), 0.0);
                client.addAccount(newAccount);
                accountRepository.save(newAccount);

                return new ResponseEntity<>(HttpStatus.CREATED);
        }
}




