package com.mindhub.homebanking.security;

import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizationTest extends IntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clients/current")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clients/" + ids.clientId())).andExpect(status().isUnauthorized());
    }

    @Test
    void clientCannotListAllClients() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotReadAnotherClientById() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/clients/" + ids.otherClientId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientSeesOnlyOwnDataThroughCurrent() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/clients/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TestData.CLIENT_EMAIL))
                .andExpect(jsonPath("$.accounts", hasSize(1)))
                .andExpect(jsonPath("$.accounts[0].number").value("vin001"))
                .andExpect(jsonPath("$.accounts[0].balance").value(5000.00))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void adminCanListAndReadClients() throws Exception {
        String token = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].password").doesNotExist());
        mvc.perform(get("/api/clients/" + ids.otherClientId()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(TestData.OTHER_CLIENT_EMAIL));
    }

    @Test
    void missingClientIsProblemDetail404() throws Exception {
        String token = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get("/api/clients/999999").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mvc.perform(get("/api/clients/current").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("homebanking").subject(TestData.ADMIN_EMAIL).claim("role", "ADMIN")
                .issuedAt(past.minusSeconds(900)).expiresAt(past).build();
        String expired = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("evil").subject(TestData.ADMIN_EMAIL).claim("role", "ADMIN")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        String foreign = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, bearer(foreign)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removedEndpointsAreNotReachable() throws Exception {
        String admin = accessToken(TestData.ADMIN_EMAIL);
        // Spring Data REST and the entity-returning /accounts were removed; unknown paths are denied by default.
        for (String path : new String[] {"/rest/clients", "/rest/accounts", "/accounts", "/h2-console"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());
        }
    }

    @Test
    void actuatorExposesOnlyHealthPublicly() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        String client = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, bearer(client)))
                .andExpect(status().isForbidden());
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get("/api/clients/current").secure(true).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/clients")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
        mvc.perform(options("/api/clients")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
