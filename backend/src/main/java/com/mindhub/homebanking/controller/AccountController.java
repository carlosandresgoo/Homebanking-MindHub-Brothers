package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
public class AccountController {

        @Autowired
        private AccountRepository repo;

        @RequestMapping("/accounts")
        public List<Account> getAll() {
            return repo.findAll();
        }
}

