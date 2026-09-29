package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContactControllerTest extends IntegrationTest {

    private static final String URL = "/api/clients/current/contacts";

    private ResultActions add(String token, String accountNumber, String alias) throws Exception {
        return mvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("accountNumber", accountNumber, "alias", alias))));
    }

    @Test
    void addsListsRenamesAndDeletesRecipients() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);

        MvcResult created = add(melba, " vin999 ", "  Otro   cliente (trabajo) ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("VIN999"))
                .andExpect(jsonPath("$.alias").value("Otro cliente (trabajo)"))
                .andExpect(jsonPath("$.holderDisplay").value("Other C."))
                .andReturn();
        long id = body(created).get("id").asLong();

        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].alias").value("Otro cliente (trabajo)"));

        mvc.perform(patch(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("alias", "Alquiler"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("Alquiler"));

        mvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isNoContent());
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsUnknownOwnAndDuplicateAccounts() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        add(melba, "VIN-00000000", "Nadie").andExpect(status().isNotFound());
        add(melba, "VIN001", "Mía").andExpect(status().isUnprocessableEntity());

        add(melba, "VIN999", "Otro").andExpect(status().isCreated());
        add(melba, "vin999", "Otra vez").andExpect(status().isConflict());

        testData.account(TestData.OTHER_CLIENT_EMAIL, "VIN998", BigDecimal.ZERO);
        add(melba, "VIN998", "OTRO").andExpect(status().isConflict()); // aliases are case-insensitive
    }

    @Test
    void validatesTheAlias() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        MvcResult result = add(melba, "VIN999", "<script>").andExpect(status().isBadRequest()).andReturn();
        assertThat(body(result).get("errors").has("alias")).isTrue();
        add(melba, "VIN999", "a".repeat(41)).andExpect(status().isBadRequest());
        add(melba, "VIN999", " ").andExpect(status().isBadRequest());
    }

    @Test
    void anotherClientsRecipientIsNotFound() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        long id = body(add(melba, "VIN999", "Otro").andReturn()).get("id").asLong();

        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        mvc.perform(patch(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("alias", "Mío"))))
                .andExpect(status().isNotFound());
        mvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void addingIsRateLimited() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        for (int i = 0; i < 5; i++) {
            add(melba, "VIN-0000000" + i, "Prueba " + i).andExpect(status().isNotFound());
        }
        add(melba, "VIN999", "Otro").andExpect(status().isTooManyRequests());
    }

    @Test
    void onlyClientsHaveRecipients() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());
    }
}
