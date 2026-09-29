package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Account;
import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Role;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.dto.CreateClientRequest;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.mapper.ClientMapper;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.repository.RefreshTokenRepository;
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
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService audit;
    private final Clock clock;

    public ClientService(ClientRepository clientRepository, AccountRepository accountRepository,
                         AccountNumberGenerator accountNumbers, ClientMapper clientMapper,
                         PasswordEncoder passwordEncoder, RefreshTokenRepository refreshTokenRepository,
                         AuditService audit, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.audit = audit;
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

    /**
     * Admin block/unblock. Only CLIENT accounts can be blocked (an admin cannot lock out another admin
     * or themselves); blocking revokes every refresh token so existing sessions end within one access
     * token lifetime. Unblocking also clears a temporary lockout.
     */
    @Transactional
    public ClientDTO setEnabled(Long id, boolean enabled) {
        Client client = clientRepository.findWithAccountsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        if (client.getRole() != Role.CLIENT) {
            throw new BusinessRuleException("Only client accounts can be blocked or unblocked");
        }
        if (enabled) {
            client.unblock();
        } else {
            client.block();
            refreshTokenRepository.revokeAllByClient(client);
        }
        audit.success(enabled ? AuditAction.CLIENT_UNBLOCKED : AuditAction.CLIENT_BLOCKED, client.getEmail(), null);
        return clientMapper.toDto(client);
    }

    /** Admin creation (POST /api/clients). */
    @Transactional
    public ClientDTO create(CreateClientRequest request) {
        Client client = register(request);
        audit.success(AuditAction.CLIENT_CREATED, client.getEmail(), null);
        return clientMapper.toDto(client);
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
