package com.mindhub.homebanking.Controllers;


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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import static java.util.stream.Collectors.toList;


@RestController
public class ClientController {

    @Autowired
    private PasswordEncoder passwordEncoder;
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

    @RequestMapping("/api/clients")
    public List<ClientDTO> getClient() {
        return clientService.getClient();
    }

    @RequestMapping("/api/clients/current")
    public ClientDTO getClients(Authentication authentication) {
        return clientService.getClients(authentication);
    }


    @RequestMapping(path = "/api/clients", method = RequestMethod.POST)

    public ResponseEntity<Object> register(
            @RequestParam String firstName, @RequestParam String lastName,
            @RequestParam String email, @RequestParam String password) {

        if (firstName.isBlank()) {
            return new ResponseEntity<>("Please complete your firstName on the form.", HttpStatus.FORBIDDEN);
        } else if (!firstName.matches("^[a-zA-Z]*$")) {
            return new ResponseEntity<>("Please enter a valid firstName. Only letters are allowed.", HttpStatus.FORBIDDEN);
        }

        if (lastName.isBlank()) {
            return new ResponseEntity<>("Please complete your lastName on the form.", HttpStatus.FORBIDDEN);
        } else if (!lastName.matches("^[a-zA-Z]*$")) {
            return new ResponseEntity<>("Please enter a valid lastName. Only letters are allowed.", HttpStatus.FORBIDDEN);
        }

        if (email.isBlank()) {
            return new ResponseEntity<>("Please complete your email on the form.", HttpStatus.FORBIDDEN);
        } else if (!email.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")) {
            return new ResponseEntity<>("Please enter a valid email address.", HttpStatus.FORBIDDEN);
        }

        if (password.isBlank()) {
            return new ResponseEntity<> ("Please complete your password on the form.", HttpStatus.FORBIDDEN);
        }

        if (clientService.findByEmail(email) !=  null) {
            return new ResponseEntity<>("Email already in use", HttpStatus.FORBIDDEN);
        }
        Client newClient = new Client(firstName, lastName, email, passwordEncoder.encode(password));
        clientService.saveClient(newClient);
        String accountNumber = randomNumber();
        Account newAccount = new Account(accountNumber, LocalDateTime.now(), 0.0,true);
        newClient.addAccount(newAccount);
        accountService.saveAccount(newAccount);
        
        return new ResponseEntity<>(HttpStatus.CREATED);

    }


  }

