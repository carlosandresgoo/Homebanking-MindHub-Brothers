package com.mindhub.homebanking.services;

import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.models.Account;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface AccountService {
    List<AccountDTO> getAccount(Authentication authentication);
    void saveAccount(Account account);
    AccountDTO getAccountDT0(Long id);
    Account findByNumber(String number);

}
