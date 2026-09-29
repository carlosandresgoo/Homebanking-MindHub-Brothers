package com.mindhub.homebanking.repository;

import com.mindhub.homebanking.domain.Card;
import com.mindhub.homebanking.domain.CardColor;
import com.mindhub.homebanking.domain.CardType;
import com.mindhub.homebanking.domain.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByClientEmailIgnoreCaseAndActiveTrueOrderByIdAsc(String email);

    boolean existsByClientAndTypeAndColorAndActiveTrue(Client client, CardType type, CardColor color);

    boolean existsByNumberHash(String numberHash);

    @EntityGraph(attributePaths = "client")
    Optional<Card> findWithClientById(Long id);
}
