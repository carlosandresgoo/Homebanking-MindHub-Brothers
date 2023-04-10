package com.mindhub.homebanking.Controllers;
import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.repositories.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import static java.util.stream.Collectors.toList;


@RestController
public class AccountController {

        @Autowired
        private AccountRepository repository;

        @RequestMapping("/api/accounts")
        public List<AccountDTO> getAccounts() {
                return repository.findAll().stream().map(accounts -> new AccountDTO(accounts)).collect(toList());
        }

        @RequestMapping("/api/accounts/{id}")
        public AccountDTO getAccount (@PathVariable Long id){
                return repository.findById(id).map(account -> new AccountDTO(account)).orElse(null);
        }
}

