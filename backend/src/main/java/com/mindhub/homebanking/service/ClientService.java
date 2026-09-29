package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.domain.Role;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.dto.CreateClientRequest;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.mapper.ClientMapper;
import com.mindhub.homebanking.repository.ClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;
    private final PasswordEncoder passwordEncoder;

    public ClientService(ClientRepository clientRepository, ClientMapper clientMapper, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.clientMapper = clientMapper;
        this.passwordEncoder = passwordEncoder;
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

    /** New clients always get the CLIENT role; promoting to ADMIN is not exposed through the API. */
    @Transactional
    public ClientDTO create(CreateClientRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (clientRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }
        Client client = new Client(
                request.name().trim(),
                request.lastName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.CLIENT);
        return clientMapper.toDto(clientRepository.save(client));
    }
}
