package com.mindhub.homebanking.config;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Card;
import com.mindhub.homebanking.domain.CardColor;
import com.mindhub.homebanking.domain.CardType;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.ClientLoan;
import com.mindhub.homebanking.domain.Loan;
import com.mindhub.homebanking.domain.Role;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.CardRepository;
import com.mindhub.homebanking.repository.ClientLoanRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.LoanRepository;
import com.mindhub.homebanking.service.CardNumberGenerator;
import com.mindhub.homebanking.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static com.mindhub.homebanking.domain.TransactionCategory.DEPOSIT;
import static com.mindhub.homebanking.domain.TransactionCategory.LOAN_DISBURSEMENT;
import static com.mindhub.homebanking.domain.TransactionCategory.LOAN_PAYMENT;
import static com.mindhub.homebanking.domain.TransactionCategory.OTHER;

/**
 * Sample data for local development only. Both users share one password taken from
 * {@code DEV_SEED_PASSWORD}; if unset, a random one is generated and printed once.
 */
@Component
@Profile("dev")
class DevDataSeeder implements ApplicationRunner {

    static final String CLIENT_EMAIL = "melba@gmail.com";
    static final String ADMIN_EMAIL = "admin@mindhub.com";

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final CardNumberGenerator cardNumbers;
    private final LoanRepository loanRepository;
    private final ClientLoanRepository clientLoanRepository;
    private final PasswordEncoder passwordEncoder;
    private final String configuredPassword;

    DevDataSeeder(ClientRepository clientRepository, AccountRepository accountRepository,
                  TransactionRepository transactionRepository, CardRepository cardRepository,
                  CardNumberGenerator cardNumbers, LoanRepository loanRepository,
                  ClientLoanRepository clientLoanRepository, PasswordEncoder passwordEncoder,
                  @Value("${DEV_SEED_PASSWORD:}") String configuredPassword) {
        this.loanRepository = loanRepository;
        this.clientLoanRepository = clientLoanRepository;
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.cardRepository = cardRepository;
        this.cardNumbers = cardNumbers;
        this.passwordEncoder = passwordEncoder;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (clientRepository.count() > 0) {
            return;
        }
        String password = configuredPassword.isBlank() ? randomPassword() : configuredPassword;
        String hash = passwordEncoder.encode(password);

        Client melba = clientRepository.save(new Client("Melba", "Morel", CLIENT_EMAIL, hash, Role.CLIENT));
        LocalDateTime now = LocalDateTime.now();
        Account vin001 = new Account("VIN001", now.minusDays(30), BigDecimal.ZERO);
        Account vin002 = new Account("VIN002", now.minusDays(29), BigDecimal.ZERO);
        melba.addAccount(vin001);
        melba.addAccount(vin002);
        accountRepository.save(vin001);
        accountRepository.save(vin002);

        // A Personal loan of 30,000 in 12 installments (36,000 with 20% interest), 2 already paid.
        Loan personal = loanRepository.findByCode("PERSONAL").orElseThrow();
        ClientLoan loan = clientLoanRepository.save(
                new ClientLoan(melba, personal, new BigDecimal("30000.00"), 12, now.minusDays(20)));

        // Balances come from movements (in chronological order) so the history adds up:
        // VIN001 = 5,000 and VIN002 = 7,000 + 30,000 - 3,000 + 500 - 3,000 = 31,500.
        transactionRepository.saveAll(List.of(
                vin001.credit(new BigDecimal("4000.00"), DEPOSIT, "Depósito inicial", now.minusDays(30)),
                vin001.credit(new BigDecimal("2500.00"), DEPOSIT, "Sueldo septiembre", now.minusDays(10)),
                vin001.debit(new BigDecimal("1200.00"), OTHER, "Alquiler", now.minusDays(8)),
                vin001.debit(new BigDecimal("300.00"), OTHER, "Supermercado", now.minusDays(2)),
                vin002.credit(new BigDecimal("7000.00"), DEPOSIT, "Depósito inicial", now.minusDays(29)),
                vin002.credit(new BigDecimal("30000.00"), LOAN_DISBURSEMENT, "Préstamo Personal acreditado",
                        now.minusDays(20)),
                vin002.debit(loan.payInstallment(), LOAN_PAYMENT, "Cuota 1/12 préstamo Personal", now.minusDays(12)),
                vin002.credit(new BigDecimal("500.00"), OTHER, "Intereses plazo fijo", now.minusDays(5)),
                vin002.debit(loan.payInstallment(), LOAN_PAYMENT, "Cuota 2/12 préstamo Personal", now.minusDays(2))));

        LocalDate today = now.toLocalDate();
        cardRepository.saveAll(List.of(
                seedCard(melba, CardType.DEBIT, CardColor.GOLD, today.minusMonths(6)),
                seedCard(melba, CardType.CREDIT, CardColor.TITANIUM, today.minusMonths(2))));

        clientRepository.save(new Client("Admin", "Mindhub", ADMIN_EMAIL, hash, Role.ADMIN));

        if (configuredPassword.isBlank()) {
            log.warn("DEV seed users {} (CLIENT) and {} (ADMIN) created with generated password: {}",
                    CLIENT_EMAIL, ADMIN_EMAIL, password);
        } else {
            log.info("DEV seed users {} (CLIENT) and {} (ADMIN) created with DEV_SEED_PASSWORD",
                    CLIENT_EMAIL, ADMIN_EMAIL);
        }
    }

    private Card seedCard(Client owner, CardType type, CardColor color, LocalDate from) {
        String number = cardNumbers.number();
        return new Card(owner, type, color, number.substring(12), CardNumberGenerator.hash(number), from, from.plusYears(5));
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
