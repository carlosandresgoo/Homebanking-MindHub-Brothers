package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.models.Card;
import com.mindhub.homebanking.models.CardColor;
import com.mindhub.homebanking.models.CardType;
import java.time.LocalDate;
import java.util.stream.Collectors;

public class CardDTO {

    private long id;
    private String cardholder;
    private CardType type;
    private CardColor color;
    private String number;
    private int cvv;
    private LocalDate thruDate;
    private LocalDate fromDate;



    public CardDTO(Card card) {

        this.id = card.getId();

        this.cardholder = card.getCardholder();

        this.number = card.getNumber();

        this.type = card.getType();

        this.color = card.getColor();

        this.cvv = card.getCvv();

        this.thruDate = card.getThruDate();

        this.fromDate = card.getFromDate();


    }


    public long getId() {
        return id;
    }

    public String getCardholder() {
        return cardholder;
    }

    public CardType getType() {
        return type;
    }

    public CardColor getColor() {
        return color;
    }

    public String getNumber() {
        return number;
    }

    public int getCvv() {
        return cvv;
    }

    public LocalDate getThruDate() {
        return thruDate;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

}
