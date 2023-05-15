package com.mindhub.homebanking.services;

import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.models.Card;
import com.mindhub.homebanking.models.Client;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ClientService {
    List<ClientDTO> getClient();
    ClientDTO getClients(Authentication authentication);
    Client findByEmail(String email);

    void saveClient (Client client);

    Client getClientAuthenticated(Authentication authentication);
}
