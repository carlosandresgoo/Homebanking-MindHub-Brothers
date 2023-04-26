package com.mindhub.homebanking.Controllers;

import com.mindhub.homebanking.dto.AccountDTO;
import com.mindhub.homebanking.dto.CardDTO;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.models.*;
import com.mindhub.homebanking.repositories.AccountRepository;

import com.mindhub.homebanking.repositories.CardRepository;
import com.mindhub.homebanking.repositories.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RestController
public class CardController {
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private ClientRepository repository;

    public int randomNumbercvv(){
        int cardnumber;
        do {
           cardnumber = (int) (Math.random() * 899 + 100);
        } while (cardRepository.findByCvv(cardnumber) != null);
        return cardnumber;
    }

    private String generateCardNumber() {
        String cardNumber;
        do {
            int firstGroup = (int) (Math.random() * 8999 + 1000);
            int secondGroup = (int) (Math.random() * 8999 + 1000);
            int thirdGroup = (int) (Math.random() * 8999 + 1000);
            int fourthGroup = (int) (Math.random() * 8999 + 1000);
            cardNumber = firstGroup + "-" + secondGroup + "-" + thirdGroup + "-" + fourthGroup;
        } while (cardRepository.findByNumber(cardNumber) != null);
        return cardNumber;
    }

    @RequestMapping("/api/clients/current/cards")
    public List<CardDTO> getAccount(Authentication authentication) {
        return new ClientDTO(repository.findByEmail(authentication.getName())).getCards().stream().collect(toList());
    }

    @RequestMapping(path = "/api/clients/current/cards", method = RequestMethod.POST)
    public ResponseEntity<Object> createCards(Authentication authentication, @RequestParam String type, @RequestParam String color) {
        if (type.isEmpty() || color.isEmpty()) {
            return new ResponseEntity<>("Complete the form", HttpStatus.FORBIDDEN);
        }

        Client client = repository.findByEmail(authentication.getName());

        for (Card card : client.getCards()) {
            if (card.getType().equals(CardType.valueOf(type)) && card.getColor().equals(CardColor.valueOf(color))) {
                return new ResponseEntity<>("Client already has a card with this type and color", HttpStatus.FORBIDDEN);
            }
        }

        int cvvnumber = randomNumbercvv();
        String cardNumber = generateCardNumber();
        Card newCard = new Card(client.getFirtsName() + " " + client.getLastName(), CardType.valueOf(type), CardColor.valueOf(color), cardNumber, cvvnumber, LocalDate.now().plusYears(5), LocalDate.now());
        client.addCard(newCard);
        cardRepository.save(newCard);

        return new ResponseEntity<>(HttpStatus.CREATED);
    }







}
