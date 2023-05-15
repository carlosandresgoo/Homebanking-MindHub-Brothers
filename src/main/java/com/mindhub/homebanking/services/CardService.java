package com.mindhub.homebanking.services;

import com.mindhub.homebanking.dto.CardDTO;
import com.mindhub.homebanking.models.Card;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

public interface CardService {
    List<CardDTO> getCardDTO(Authentication authentication);
    void saveCard (Card card);


}
