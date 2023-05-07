package com.mindhub.homebanking.Controllers;
import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.models.Account;
import com.mindhub.homebanking.models.Client;
import com.mindhub.homebanking.repositories.AccountRepository;
import com.mindhub.homebanking.repositories.ClientRepository;
import com.mindhub.homebanking.services.AccountService;
import com.mindhub.homebanking.services.ClientService;
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
        private ClientService clientService;
        @Autowired
        private AccountService accountService;

        public String randomNumber(){
                String randomNumber;
                do {
                        int number = (int) (Math.random() * 899999 + 100000);
                        randomNumber = "VIN-" + number;
                } while (accountService.findByNumber(randomNumber) != null);
                return randomNumber;
        }

        @RequestMapping("/api/clients/current/accounts")
        public List<AccountDTO> getAccount(Authentication authentication) {
                return accountService.getAccount(authentication) ;
        }

        @RequestMapping("/api/clients/current/accounts/{id}")
        public AccountDTO getAccount (@PathVariable Long id){
                return accountService.getAccountDT0(id);
        }


        @PostMapping("/api/clients/current/accounts")
        public ResponseEntity<Object> createAccount(Authentication authentication) {

                Client client = clientService.findByEmail(authentication.getName());
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
                accountService.saveAccount(newAccount);

                return new ResponseEntity<>(HttpStatus.CREATED);
        }
}




