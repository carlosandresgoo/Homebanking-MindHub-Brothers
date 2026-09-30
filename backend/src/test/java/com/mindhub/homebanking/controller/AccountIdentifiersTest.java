package com.mindhub.homebanking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindhub.homebanking.domain.AccountAlias;
import com.mindhub.homebanking.domain.Cbu;
import com.mindhub.homebanking.repository.ContactRepository;
import com.mindhub.homebanking.service.AccountNumberGenerator;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CBU and alias: exposed on accounts, usable as transfer destinations, looked up and changed safely. */
class AccountIdentifiersTest extends IntegrationTest {

    private static final String OTHER_BANK_CBU = "2850590940090418135201";

    @Autowired
    private AccountNumberGenerator generator;

    @Autowired
    private ContactRepository contactRepository;

    private String otherCbu() throws Exception {
        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        return body(mvc.perform(get("/api/accounts/" + ids.otherAccountId())
                .header(HttpHeaders.AUTHORIZATION, bearer(other))).andReturn()).get("cbu").asText();
    }

    private MvcResult transfer(String token, String target, String amount) throws Exception {
        return mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceAccountNumber", "VIN001", "targetAccountNumber", target,
                                "amount", amount))))
                .andReturn();
    }

    private ResultActions lookup(String token, String key) throws Exception {
        return mvc.perform(get("/api/accounts/lookup").param("key", key)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions changeAlias(String token, Long accountId, String alias) throws Exception {
        return mvc.perform(patch("/api/accounts/" + accountId + "/alias")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("alias", alias))));
    }

    @Test
    void accountsExposeAValidCbuOfThisBankAndTheirAlias() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        JsonNode account = body(mvc.perform(get("/api/clients/current/accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer(melba))).andReturn()).get(0);

        assertThat(Cbu.isValid(account.get("cbu").asText())).isTrue();
        assertThat(Cbu.isOwnBank(account.get("cbu").asText())).isTrue();
        assertThat(account.get("alias").asText()).isEqualTo("vin001.test");
    }

    @Test
    void newAccountsGetUniqueValidIdentifiers() {
        Set<String> cbus = new HashSet<>();
        Set<String> aliases = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            var account = generator.newAccount(LocalDateTime.now());
            assertThat(Cbu.isValid(account.getCbu())).isTrue();
            assertThat(account.getAlias()).matches(AccountAlias.PATTERN).matches("[a-z]+\\.[a-z]+\\.[a-z]+");
            cbus.add(account.getCbu());
            aliases.add(account.getAlias());
        }
        assertThat(cbus).hasSize(200);
        assertThat(aliases).hasSizeGreaterThan(190); // 125,000 combinations; the DB check prevents clashes
    }

    @Test
    void transfersAcceptACbuWithOrWithoutSpacesOrAnAliasInAnyCase() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        String cbu = otherCbu();

        assertThat(transfer(melba, cbu, "10").getResponse().getStatus()).isEqualTo(201);
        assertThat(transfer(melba, cbu.substring(0, 8) + " " + cbu.substring(8), "10").getResponse().getStatus())
                .isEqualTo(201);
        MvcResult byAlias = transfer(melba, "  VIN999.Test ", "10");
        assertThat(byAlias.getResponse().getStatus()).isEqualTo(201);
        // The receipt and the movement name the account number, whatever was typed.
        assertThat(body(byAlias).get("targetAccountNumber").asText()).isEqualTo("VIN999");
        assertThat(body(byAlias).get("sourceBalanceAfter").decimalValue()).isEqualByComparingTo("4970.00");
    }

    @Test
    void badOrForeignCbusAreRejectedWithAReason() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        String cbu = otherCbu();
        String wrongDigit = cbu.substring(0, 21) + ((cbu.charAt(21) - '0' + 1) % 10);

        MvcResult invalid = transfer(melba, wrongDigit, "10");
        assertThat(invalid.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(invalid).get("code").asText()).isEqualTo("INVALID_CBU");

        MvcResult foreign = transfer(melba, OTHER_BANK_CBU, "10");
        assertThat(foreign.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(foreign).get("code").asText()).isEqualTo("OTHER_BANK");

        // Valid and ours, but nobody has it.
        assertThat(transfer(melba, Cbu.of("9999999999999"), "10").getResponse().getStatus()).isEqualTo(404);
        assertThat(transfer(melba, "nadie.tiene.esto", "10").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void ownAliasAsDestinationOfTheSameAccountIsRejected() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        assertThat(transfer(melba, "vin001.test", "10").getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void lookupShowsAMaskedHolderAndTheAccountNumberToTransferTo() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        lookup(melba, "vin999.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("VIN999"))
                .andExpect(jsonPath("$.holderDisplay").value("Other C."))
                .andExpect(jsonPath("$.bank").value("MindHub Brothers"))
                .andExpect(jsonPath("$.own").value(false));
        lookup(melba, otherCbu()).andExpect(jsonPath("$.accountNumber").value("VIN999"));
        lookup(melba, "VIN001").andExpect(jsonPath("$.own").value(true));
        lookup(melba, "nadie.tiene.esto").andExpect(status().isNotFound());
        lookup(melba, OTHER_BANK_CBU).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OTHER_BANK"));
    }

    @Test
    void lookupIsForClientsOnlyAndRateLimited() throws Exception {
        mvc.perform(get("/api/accounts/lookup").param("key", "VIN999")).andExpect(status().isUnauthorized());
        lookup(accessToken(TestData.ADMIN_EMAIL), "VIN999").andExpect(status().isForbidden());
        String melba = accessToken(TestData.CLIENT_EMAIL);
        lookup(melba, "").andExpect(status().isBadRequest()); // rejected before counting

        for (int i = 0; i < 5; i++) { // app.security.login-rate-limit: 5 per minute
            lookup(melba, "VIN999").andExpect(status().isOk());
        }
        lookup(melba, "VIN999").andExpect(status().isTooManyRequests());
    }

    @Test
    void theOwnerCanChangeTheAliasAndReceiveWithIt() throws Exception {
        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        changeAlias(other, ids.otherAccountId(), "Sol.Rio.Mate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("sol.rio.mate"));

        String melba = accessToken(TestData.CLIENT_EMAIL);
        assertThat(transfer(melba, "sol.rio.mate", "10").getResponse().getStatus()).isEqualTo(201);
        assertThat(transfer(melba, "vin999.test", "10").getResponse().getStatus()).as("old alias").isEqualTo(404);
    }

    @Test
    void aliasChangesAreValidatedAndOwnerOnly() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        changeAlias(melba, ids.otherAccountId(), "robado.alias").andExpect(status().isNotFound());
        changeAlias(melba, ids.accountId(), "VIN999.TEST").andExpect(status().isConflict());
        changeAlias(melba, ids.accountId(), "VIN-12345678").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALIAS_RESERVED"));
        changeAlias(melba, ids.accountId(), "corto").andExpect(status().isBadRequest());
        changeAlias(melba, ids.accountId(), "con espacio").andExpect(status().isBadRequest());
        changeAlias(melba, ids.accountId(), "piñón.dulce").andExpect(status().isBadRequest());
        changeAlias(accessToken(TestData.ADMIN_EMAIL), ids.accountId(), "admin.alias")
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/accounts/" + ids.accountId() + "/alias").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("alias", "sin.token")))).andExpect(status().isUnauthorized());
    }

    @Test
    void contactsCanBeSavedByAliasAndKeepTheAccountNumber() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(post("/api/clients/current/contacts").header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNumber", "vin999.test", "alias", "Otro"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("VIN999"));
        assertThat(contactRepository.findAll()).singleElement()
                .satisfies(c -> assertThat(c.getAccountNumber()).isEqualTo("VIN999"));
    }
}
