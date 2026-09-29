package com.mindhub.homebanking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindhub.homebanking.repository.CardRepository;
import com.mindhub.homebanking.service.CardNumberGenerator;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardControllerTest extends IntegrationTest {

    @Autowired
    private CardRepository cardRepository;

    private MvcResult issue(String token, String type, String color) throws Exception {
        return mvc.perform(post("/api/clients/current/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", type, "color", color))))
                .andReturn();
    }

    @Test
    void issuingReturnsTheFullNumberAndCvvOnlyOnce() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        MvcResult result = issue(token, "DEBIT", "GOLD");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        JsonNode body = body(result);
        String number = body.get("number").asText();
        assertThat(number).matches("\\d{16}");
        assertThat(CardNumberGenerator.isLuhnValid(number)).isTrue();
        assertThat(body.get("cvv").asText()).matches("\\d{3}");
        assertThat(body.get("card").get("last4").asText()).isEqualTo(number.substring(12));
        assertThat(body.get("card").get("cardholder").asText()).isEqualTo("Melba Morel");
        assertThat(body.get("card").get("thruDate").asText()).isEqualTo(LocalDate.now().plusYears(5).toString());

        // Listing never exposes the PAN or the CVV again...
        String list = mvc.perform(get("/api/clients/current/cards").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].number").doesNotExist())
                .andExpect(jsonPath("$[0].cvv").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(number);

        // ...and the database only keeps the last 4 digits and a hash.
        assertThat(cardRepository.findAll()).singleElement().satisfies(card -> {
            assertThat(card.getLast4()).isEqualTo(number.substring(12));
            assertThat(card.toString()).doesNotContain(number);
        });
        assertThat(cardRepository.existsByNumberHash(CardNumberGenerator.hash(number))).isTrue();
    }

    @Test
    void onlyOneActiveCardPerTypeAndColour() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        assertThat(issue(token, "CREDIT", "SILVER").getResponse().getStatus()).isEqualTo(201);
        assertThat(issue(token, "CREDIT", "SILVER").getResponse().getStatus()).isEqualTo(409);
        assertThat(issue(token, "DEBIT", "SILVER").getResponse().getStatus()).isEqualTo(201);
        assertThat(issue(token, "CREDIT", "GOLD").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void deactivatedCardCanBeRequestedAgain() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        long id = body(issue(token, "CREDIT", "TITANIUM")).get("card").get("id").asLong();

        mvc.perform(delete("/api/cards/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/clients/current/cards").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(issue(token, "CREDIT", "TITANIUM").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void cannotDeactivateAnotherClientsCard() throws Exception {
        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        long id = body(issue(other, "DEBIT", "GOLD")).get("card").get("id").asLong();

        String melba = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(delete("/api/cards/" + id).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/clients/current/cards").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void invalidTypeOrColourIs400() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        assertThat(issue(token, "PLATINUM", "GOLD").getResponse().getStatus()).isEqualTo(400);
        assertThat(issue(token, "DEBIT", "pink").getResponse().getStatus()).isEqualTo(400);
        mvc.perform(post("/api/clients/current/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists())
                .andExpect(jsonPath("$.errors.color").exists());
    }

    @Test
    void cardsAreForClientsOnly() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        assertThat(issue(admin, "DEBIT", "GOLD").getResponse().getStatus()).isEqualTo(403);
        mvc.perform(get("/api/clients/current/cards")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/cards/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void generatedNumbersAreLuhnValidAndCvvHasThreeDigits() {
        CardNumberGenerator generator = new CardNumberGenerator();
        for (int i = 0; i < 200; i++) {
            String number = generator.number();
            assertThat(number).hasSize(16).startsWith("450799");
            assertThat(CardNumberGenerator.isLuhnValid(number)).isTrue();
            assertThat(generator.cvv()).matches("\\d{3}");
        }
        assertThat(CardNumberGenerator.isLuhnValid("4507990000000000")).isFalse();
    }

    @Test
    void issuingResponseIsNotCached() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(post("/api/clients/current/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "DEBIT", "color", "SILVER"))))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
    }
}
