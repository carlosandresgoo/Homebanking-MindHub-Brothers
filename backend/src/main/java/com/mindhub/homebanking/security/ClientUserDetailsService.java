package com.mindhub.homebanking.security;

import com.mindhub.homebanking.repository.ClientRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ClientUserDetailsService implements UserDetailsService {

    private final ClientRepository clientRepository;

    public ClientUserDetailsService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return clientRepository.findByEmailIgnoreCase(email)
                .map(client -> User.withUsername(client.getEmail())
                        .password(client.getPassword())
                        .roles(client.getRole().name())
                        .disabled(!client.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }
}
