package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Cbu;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Finds an account by what people type as a destination: its number ({@code VIN-…}), its CBU (22 digits,
 * spaces allowed) or its alias. Aliases can never look like an account number (see
 * {@link com.mindhub.homebanking.domain.AccountAlias}), so the key is never ambiguous.
 */
@Component
public class RecipientResolver {

    private final AccountRepository accountRepository;

    public RecipientResolver(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * @return the account id, or empty when nothing matches (callers answer 404)
     * @throws BusinessRuleException 422 {@code INVALID_CBU} for a CBU with wrong check digits, and
     *                               {@code OTHER_BANK} for a valid CBU of another bank (not supported yet)
     */
    public Optional<Long> resolveId(String key) {
        String trimmed = key.strip();
        String compact = trimmed.replace(" ", "");
        if (compact.matches("\\d{22}")) {
            if (!Cbu.isValid(compact)) {
                throw new BusinessRuleException("The CBU is not valid", Map.of("code", "INVALID_CBU"));
            }
            if (!Cbu.isOwnBank(compact)) {
                throw new BusinessRuleException("Transfers to other banks are not available",
                        Map.of("code", "OTHER_BANK"));
            }
            return accountRepository.findIdByCbu(compact);
        }
        return accountRepository.findIdByNumber(trimmed).or(() -> accountRepository.findIdByAlias(trimmed));
    }
}
