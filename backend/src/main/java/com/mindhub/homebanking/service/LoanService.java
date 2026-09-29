package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.ClientLoan;
import com.mindhub.homebanking.domain.Loan;
import com.mindhub.homebanking.dto.ClientLoanDTO;
import com.mindhub.homebanking.dto.LoanApplicationRequest;
import com.mindhub.homebanking.dto.LoanDTO;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientLoanRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.LoanRepository;
import com.mindhub.homebanking.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Loan applications and installment payments. Accounts (and the loan, when paying) are row-locked
 * before balances change; every lookup by id or number is restricted to the caller (404 otherwise).
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

    private final LoanRepository loanRepository;
    private final ClientLoanRepository clientLoanRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public LoanService(LoanRepository loanRepository, ClientLoanRepository clientLoanRepository,
                       ClientRepository clientRepository, AccountRepository accountRepository,
                       TransactionRepository transactionRepository, Clock clock) {
        this.loanRepository = loanRepository;
        this.clientLoanRepository = clientLoanRepository;
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    public List<LoanDTO> catalog() {
        return loanRepository.findAllByOrderByIdAsc().stream()
                .map(loan -> new LoanDTO(loan.getId(), loan.getCode(), loan.getName(), loan.getMaxAmount(),
                        loan.getInterestRate(), List.copyOf(loan.getPayments())))
                .toList();
    }

    public List<ClientLoanDTO> findMine(String email) {
        return clientLoanRepository.findByClientEmailIgnoreCaseOrderByCreatedAtDesc(email).stream()
                .map(LoanService::toDto)
                .toList();
    }

    @Transactional
    public ClientLoanDTO apply(String email, LoanApplicationRequest request) {
        Loan loan = loanRepository.findById(request.loanId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
        if (!loan.allowsAmount(request.amount())) {
            throw new BusinessRuleException("The amount exceeds the maximum for this loan");
        }
        if (!loan.allowsPayments(request.payments())) {
            throw new BusinessRuleException("That number of installments is not available for this loan");
        }
        Client client = clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        if (clientLoanRepository.hasActiveLoan(client, loan)) {
            throw new ConflictException("You already have an active " + loan.getName() + " loan");
        }
        Account account = lockOwnAccount(request.accountNumber(), email);

        LocalDateTime now = LocalDateTime.now(clock);
        ClientLoan clientLoan = clientLoanRepository.save(
                new ClientLoan(client, loan, request.amount(), request.payments(), now));
        transactionRepository.save(account.credit(request.amount(),
                "Préstamo " + loan.getName() + " acreditado", now));
        return toDto(clientLoan);
    }

    /** Pays the next installment of one of the caller's loans from one of the caller's accounts. */
    @Transactional
    public ClientLoanDTO payInstallment(Long clientLoanId, String email, String accountNumber) {
        ClientLoan clientLoan = clientLoanRepository.findByIdForUpdate(clientLoanId)
                .filter(cl -> cl.getClient().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
        if (clientLoan.isPaidOff()) {
            throw new BusinessRuleException("This loan is already paid off");
        }
        Account account = lockOwnAccount(accountNumber, email);
        BigDecimal installment = clientLoan.nextInstallment();
        if (!account.hasFunds(installment)) {
            throw new BusinessRuleException("Insufficient funds");
        }

        int number = clientLoan.getPaymentsMade() + 1;
        clientLoan.payInstallment();
        transactionRepository.save(account.debit(installment,
                "Cuota " + number + "/" + clientLoan.getPayments() + " préstamo " + clientLoan.getLoan().getName(),
                LocalDateTime.now(clock)));
        return toDto(clientLoan);
    }

    private Account lockOwnAccount(String number, String email) {
        return accountRepository.findIdByNumber(number.trim().toUpperCase(Locale.ROOT))
                .flatMap(accountRepository::findByIdForUpdate)
                .filter(Account::isActive)
                .filter(a -> a.getClient().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private static ClientLoanDTO toDto(ClientLoan cl) {
        Loan loan = cl.getLoan();
        return new ClientLoanDTO(cl.getId(), loan.getId(), loan.getCode(), loan.getName(), cl.getAmount(),
                cl.getTotalDue(), cl.getPayments(), cl.getPaymentsMade(), cl.nextInstallment(), cl.getOutstanding(),
                cl.isPaidOff(), cl.getCreatedAt());
    }
}
