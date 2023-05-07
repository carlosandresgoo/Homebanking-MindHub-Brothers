package com.mindhub.homebanking.Controllers;

import com.mindhub.homebanking.dto.CardDTO;
import com.mindhub.homebanking.dto.ClientDTO;
import com.mindhub.homebanking.models.*;
import com.mindhub.homebanking.repositories.AccountRepository;
import com.mindhub.homebanking.repositories.CardRepository;
import com.mindhub.homebanking.repositories.ClientRepository;
import com.mindhub.homebanking.services.AccountService;
import com.mindhub.homebanking.services.CardService;
import com.mindhub.homebanking.services.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;

import static java.util.stream.Collectors.toList;

@RestController
public class CardController {
    @Autowired
    private CardService cardService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private AccountService accountService;
//    @Autowired
//    private CardRepository cardRepository;
//    @Autowired
//    private AccountRepository accountRepository;
//    @Autowired
//    private ClientRepository repository;

    public int randomNumbercvv(){
        int cardnumber;
           cardnumber = (int) (Math.random() * 899 + 100);
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
        } while (accountService.findByNumber(cardNumber) != null);
        return cardNumber;
    }

    @RequestMapping("/api/clients/current/cards")
    public List<CardDTO> getAccount(Authentication authentication) {
        return cardService.getCardDTO(authentication);
    }

    @RequestMapping(path = "/api/clients/current/cards", method = RequestMethod.POST)
    public ResponseEntity<Object> createCards(Authentication authentication, @RequestParam String type, @RequestParam String color) {


        if (!type.equalsIgnoreCase("DEBIT") && !type.equalsIgnoreCase("CREDIT")) {
            return new ResponseEntity<>("Please enter a valid type. Only 'DEBIT' and 'CREDIT' are allowed.", HttpStatus.FORBIDDEN);
        }

        if (!color.matches("^(GOLD|SILVER|TITANIUM)$")) {
            return new ResponseEntity<>("Please enter a valid color. Only 'GOLD', 'SILVER' and 'TITANIUM' are allowed in uppercase letters.", HttpStatus.FORBIDDEN);
        }

        Client client = clientService.findByEmail(authentication.getName());

        if(client == null) {
            return new ResponseEntity<>("you can't create a card because you're not a client.", HttpStatus.NOT_FOUND);
        }

        for (Card card : client.getCards()) {
            if (card.getType().equals(CardType.valueOf(type)) && card.getColor().equals(CardColor.valueOf(color))) {
                return new ResponseEntity<>("you already have this type of card", HttpStatus.FORBIDDEN);
            }
        }

        int cvvnumber = randomNumbercvv();
        String cardNumber = generateCardNumber();
        Card newCard = new Card(client.getFirtsName() + " " + client.getLastName(), CardType.valueOf(type), CardColor.valueOf(color), cardNumber, cvvnumber, LocalDate.now().plusYears(5), LocalDate.now());
        client.addCard(newCard);
        cardService.saveCard(newCard);

        return new ResponseEntity<>(HttpStatus.CREATED);
    }




}

