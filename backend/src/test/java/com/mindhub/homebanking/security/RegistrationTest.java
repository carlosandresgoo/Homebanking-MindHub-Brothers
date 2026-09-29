package com.mindhub.homebanking.security;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationTest extends IntegrationTest {

    private static final Map<String, Object> VALID = Map.of(
            "name", "Chloe",
            "lastName", "Obrian",
            "email", "chloe@test.com",
            "password", "a-long-enough-password");

    private MvcResult register(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn();
    }

    @Test
    void signUpCreatesAClientWithAnAccountAndLogsIn() throws Exception {
        MvcResult result = register(VALID);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).get("role").asText()).isEqualTo("CLIENT");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("refresh_token=", "HttpOnly");

        String token = body(result).get("accessToken").asText();
        mvc.perform(get("/api/clients/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("chloe@test.com"))
                .andExpect(jsonPath("$.accounts", hasSize(1)))
                .andExpect(jsonPath("$.accounts[0].number").value(matchesPattern("VIN-\\d{8}")))
                .andExpect(jsonPath("$.accounts[0].balance").value(0));
    }

    @Test
    void signUpCannotChooseTheRole() throws Exception {
        Map<String, Object> malicious = new HashMap<>(VALID);
        malicious.put("role", "ADMIN");
        assertThat(body(register(malicious)).get("role").asText()).isEqualTo("CLIENT");
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        Map<String, Object> duplicate = new HashMap<>(VALID);
        duplicate.put("email", TestData.CLIENT_EMAIL);
        assertThat(register(duplicate).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void weakInputIs400() throws Exception {
        Map<String, Object> weak = new HashMap<>(VALID);
        weak.put("password", "short");
        weak.put("email", "nope");
        MvcResult result = register(weak);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).get("errors").has("password")).isTrue();
        assertThat(body(result).get("errors").has("email")).isTrue();
    }

    @Test
    void signUpIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            Map<String, Object> body = new HashMap<>(VALID);
            body.put("email", "user" + i + "@test.com");
            assertThat(register(body).getResponse().getStatus()).isEqualTo(201);
        }
        Map<String, Object> sixth = new HashMap<>(VALID);
        sixth.put("email", "user6@test.com");
        assertThat(register(sixth).getResponse().getStatus()).isEqualTo(429);
    }
}
