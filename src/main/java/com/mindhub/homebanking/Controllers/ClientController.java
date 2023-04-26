package com.mindhub.homebanking.Controllers;


import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.models.Account;
import com.mindhub.homebanking.models.Client;
import com.mindhub.homebanking.repositories.AccountRepository;
import com.mindhub.homebanking.repositories.ClientRepository;
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
    private ClientRepository  repository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;


    public String randomNumber(){
        String randomNumber;
        do {
            int number = (int) (Math.random() * 899999 + 100000);
            randomNumber = "VIN-" + number;
        } while (accountRepository.findByNumber(randomNumber) != null);
        return randomNumber;
    }

    @RequestMapping("/api/clients")
    public List<ClientDTO> getClient() {
        return repository.findAll().stream().map(client -> new ClientDTO(client)).collect(toList());
    }


    @RequestMapping("/api/clients/current")
    public ClientDTO getClient(Authentication authentication) {
        return new ClientDTO(repository.findByEmail(authentication.getName()));
    }


    @RequestMapping(path = "/api/clients", method = RequestMethod.POST)

    public ResponseEntity<Object> register(
            @RequestParam String firstName, @RequestParam String lastName,
            @RequestParam String email, @RequestParam String password) {
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            return new ResponseEntity<>("Missing data", HttpStatus.FORBIDDEN);
        }

        if (repository.findByEmail(email) !=  null) {
            return new ResponseEntity<>("Email already in use", HttpStatus.FORBIDDEN);
        }
        Client newClient = new Client(firstName, lastName, email, passwordEncoder.encode(password));
        repository.save(newClient);
        String accountNumber = randomNumber();
        Account newAccount = new Account(accountNumber, LocalDateTime.now(), 0.0);
        newClient.addAccount(newAccount);
        accountRepository.save(newAccount);
        
        return new ResponseEntity<>(HttpStatus.CREATED);

    }


  }

