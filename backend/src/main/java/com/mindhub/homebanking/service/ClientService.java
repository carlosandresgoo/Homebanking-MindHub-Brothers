package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Role;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.dto.CreateClientRequest;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.mapper.ClientMapper;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumbers;
    private final ClientMapper clientMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ClientService(ClientRepository clientRepository, AccountRepository accountRepository,
                         AccountNumberGenerator accountNumbers, ClientMapper clientMapper,
                         PasswordEncoder passwordEncoder, Clock clock) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.accountNumbers = accountNumbers;
        this.clientMapper = clientMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public List<ClientDTO> findAll() {
        return clientRepository.findAllByOrderByIdAsc().stream().map(clientMapper::toDto).toList();
    }

    public ClientDTO findById(Long id) {
        return clientRepository.findWithAccountsById(id)
                .map(clientMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    public ClientDTO findByEmail(String email) {
        return clientRepository.findWithAccountsByEmailIgnoreCase(email)
                .map(clientMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    /** Admin creation (POST /api/clients). */
    @Transactional
    public ClientDTO create(CreateClientRequest request) {
        return clientMapper.toDto(register(request));
    }

    /**
     * Creates a CLIENT with an initial empty account (as in the original app). New clients always get
     * the CLIENT role; promoting to ADMIN is not exposed through the API.
     */
    @Transactional
    public Client register(CreateClientRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (clientRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }
        Client client = clientRepository.save(new Client(
                request.name().trim(),
                request.lastName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.CLIENT));
        Account account = new Account(accountNumbers.next(), LocalDateTime.now(clock), BigDecimal.ZERO);
        client.addAccount(account);
        accountRepository.save(account);
        return client;
    }
}
