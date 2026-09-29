package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientControllerTest extends IntegrationTest {

    private static final Map<String, Object> VALID = Map.of(
            "name", "Chloe",
            "lastName", "Obrian",
            "email", "Chloe@Test.com",
            "password", "a-long-enough-password");

    @Test
    void adminCreatesClientWithClientRoleAndAnEmptyAccount() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);

        mvc.perform(post("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(VALID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost/api/clients/")))
                .andExpect(jsonPath("$.email").value("chloe@test.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.accounts.length()").value(1))
                .andExpect(jsonPath("$.accounts[0].number").value(org.hamcrest.Matchers.matchesPattern("VIN-\\d{8}")))
                .andExpect(jsonPath("$.accounts[0].balance").value(0))
                .andExpect(jsonPath("$.password").doesNotExist());

        // The new client can log in with the password (stored hashed).
        assertThat(login("chloe@test.com", "a-long-enough-password").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void massAssignmentOfRoleAndIdIsIgnored() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        Map<String, Object> malicious = new java.util.HashMap<>(VALID);
        malicious.put("role", "ADMIN");
        malicious.put("id", 1);
        malicious.put("accounts", java.util.List.of(Map.of("number", "vin777", "balance", 1_000_000)));

        MvcResult result = mvc.perform(post("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(malicious)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(body(result).get("role").asText()).isEqualTo("CLIENT");
        // Only the server-created empty account: the injected one (with a million) is ignored.
        assertThat(body(result).get("accounts")).hasSize(1);
        assertThat(body(result).get("accounts").get(0).get("number").asText()).isNotEqualTo("vin777");
        assertThat(body(result).get("accounts").get(0).get("balance").decimalValue()).isZero();
        assertThat(body(result).get("id").asLong()).isNotEqualTo(1L);
    }

    @Test
    void clientRoleCannotCreateClients() throws Exception {
        String client = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(post("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(client))
                        .contentType(MediaType.APPLICATION_JSON).content(json(VALID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateClients() throws Exception {
        mvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(json(VALID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInputIs400WithFieldErrorsAndNoEcho() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        Map<String, Object> invalid = Map.of(
                "name", "Chl0e<script>",
                "lastName", "",
                "email", "not-an-email",
                "password", "short");

        MvcResult result = mvc.perform(post("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.lastName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain("<script>").doesNotContain("short");
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        Map<String, Object> duplicate = new java.util.HashMap<>(VALID);
        duplicate.put("email", TestData.CLIENT_EMAIL.toUpperCase());

        mvc.perform(post("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(duplicate)))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedJsonIs400WithoutInternals() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        MvcResult result = mvc.perform(post("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Exception")
                .doesNotContain("com.fasterxml");
    }
}
