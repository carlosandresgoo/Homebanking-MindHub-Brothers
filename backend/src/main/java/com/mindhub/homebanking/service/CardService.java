package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.AuditAction;
import com.mindhub.homebanking.domain.Card;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.dto.CardDTO;
import com.mindhub.homebanking.dto.IssueCardRequest;
import com.mindhub.homebanking.dto.IssuedCardDTO;
import com.mindhub.homebanking.exception.ConflictException;
import com.mindhub.homebanking.exception.ResourceNotFoundException;
import com.mindhub.homebanking.repository.CardRepository;
import com.mindhub.homebanking.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Card use cases. One active card per type + colour; lookups by id are owner-only (404 otherwise). */
@Service
@Transactional(readOnly = true)
public class CardService {

    static final int VALIDITY_YEARS = 5;

    private final CardRepository cardRepository;
    private final ClientRepository clientRepository;
    private final CardNumberGenerator generator;
    private final AuditService audit;
    private final Clock clock;

    public CardService(CardRepository cardRepository, ClientRepository clientRepository,
                       CardNumberGenerator generator, AuditService audit, Clock clock) {
        this.audit = audit;
        this.cardRepository = cardRepository;
        this.clientRepository = clientRepository;
        this.generator = generator;
        this.clock = clock;
    }

    public List<CardDTO> findMine(String email) {
        LocalDate today = LocalDate.now(clock);
        return cardRepository.findByClientEmailIgnoreCaseAndActiveTrueOrderByIdAsc(email).stream()
                .map(card -> toDto(card, today))
                .toList();
    }

    @Transactional
    public IssuedCardDTO issue(String email, IssueCardRequest request) {
        Client client = clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        if (cardRepository.existsByClientAndTypeAndColorAndActiveTrue(client, request.type(), request.color())) {
            throw new ConflictException("You already have an active " + request.color() + " " + request.type() + " card");
        }
        String number;
        do {
            number = generator.number();
        } while (cardRepository.existsByNumberHash(CardNumberGenerator.hash(number)));

        LocalDate today = LocalDate.now(clock);
        Card card = cardRepository.save(new Card(
                client, request.type(), request.color(), number.substring(number.length() - 4),
                CardNumberGenerator.hash(number), today, today.plusYears(VALIDITY_YEARS)));
        audit.success(AuditAction.CARD_ISSUED, describe(card), null);
        return new IssuedCardDTO(toDto(card, today), number, generator.cvv());
    }

    /** Soft delete by the owner. Another client's card answers 404 (task11 let anyone deactivate any card). */
    @Transactional
    public void deactivate(Long id, String email) {
        Card card = cardRepository.findWithClientById(id)
                .filter(Card::isActive)
                .filter(c -> c.getClient().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
        card.deactivate();
        audit.success(AuditAction.CARD_DEACTIVATED, describe(card), null);
    }

    /** Masked description for the audit trail (never the full number). */
    private static String describe(Card card) {
        return card.getType() + " " + card.getColor() + " ****" + card.getLast4();
    }

    private static CardDTO toDto(Card card, LocalDate today) {
        return new CardDTO(card.getId(), card.getCardholder(), card.getType(), card.getColor(), card.getLast4(),
                card.getFromDate(), card.getThruDate(), card.isExpired(today));
    }
}
