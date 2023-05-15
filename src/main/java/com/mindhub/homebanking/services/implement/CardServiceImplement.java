package com.mindhub.homebanking.services.implement;

import com.mindhub.homebanking.dto.CardDTO;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.models.Card;
import com.mindhub.homebanking.repositories.CardRepository;
import com.mindhub.homebanking.services.CardService;
import com.mindhub.homebanking.services.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;


import static java.util.stream.Collectors.toList;
@Service
public class CardServiceImplement implements CardService {
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private ClientService clientService;


    @Override
    public List<CardDTO> getCardDTO(Authentication authentication) {
        return new ClientDTO(clientService.findByEmail(authentication.getName())).getCards().stream().collect(toList());
    }

    @Override
    public void saveCard(Card card) {
        cardRepository.save(card);
    }




    }





