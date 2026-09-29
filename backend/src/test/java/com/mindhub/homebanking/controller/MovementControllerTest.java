package com.mindhub.homebanking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindhub.homebanking.domain.TransactionCategory;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MovementControllerTest extends IntegrationTest {

    private static final LocalDateTime AUG_10 = LocalDateTime.of(2026, 8, 10, 9, 30);

    private String url() {
        return "/api/accounts/" + ids.accountId() + "/transactions";
    }

    /** Melba's VIN001: the initial deposit (today) plus three movements in August. */
    private void august() {
        testData.movement("VIN001", false, "1200.00", TransactionCategory.OTHER, "Alquiler agosto", AUG_10);
        testData.movement("VIN001", true, "2500.00", TransactionCategory.DEPOSIT, "Sueldo 100% agosto",
                AUG_10.plusDays(5));
        testData.movement("VIN001", false, "300.00", TransactionCategory.OTHER, "=HYPERLINK(\"x\")",
                AUG_10.plusDays(20));
    }

    @Test
    void pagesNewestFirst() throws Exception {
        for (int i = 1; i <= 24; i++) {
            testData.movement("VIN001", true, "1.00", TransactionCategory.DEPOSIT, "Depósito " + i,
                    AUG_10.plusHours(i));
        }
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get(url()).param("page", "1").param("size", "10").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content", hasSize(10)))
                // Page 0: today's deposit + "Depósito 24".."Depósito 16"; page 1 starts at 15.
                .andExpect(jsonPath("$.content[0].description").value("Depósito 15"));
    }

    @Test
    void filtersByDatesTypeCategoryAndText() throws Exception {
        august();
        String token = accessToken(TestData.CLIENT_EMAIL);

        mvc.perform(get(url()).param("from", "2026-08-10").param("to", "2026-08-15")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2)); // "to" is inclusive
        mvc.perform(get(url()).param("type", "DEBIT").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(url()).param("category", "DEPOSIT").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(url()).param("q", "ALQUILER").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
        // % is matched literally, not as a wildcard.
        mvc.perform(get(url()).param("q", "100%").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(url()).param("q", "%").param("type", "DEBIT").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsInvalidFilters() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(get(url()).param("from", "2026-09-10").param("to", "2026-09-01")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(url()).param("size", "101").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url()).param("type", "BOTH").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url()).param("from", "10/09/2026").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(url()).param("q", "x".repeat(51)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportsCsvReadyForASpreadsheetAndSafeFromFormulas() throws Exception {
        august();
        String token = accessToken(TestData.CLIENT_EMAIL);
        MvcResult result = mvc.perform(get(url() + "/export").param("from", "2026-08-01").param("to", "2026-08-31")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment; filename=\"movimientos-VIN001-").endsWith(".csv\"");

        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo("﻿Fecha;Descripción;Categoría;Tipo;Importe;Saldo");
        assertThat(lines).hasSize(4);
        // Newest first; the formula-looking description is neutralised and quoted (it has quotes).
        assertThat(lines[1]).isEqualTo("30/08/2026 09:30;\"'=HYPERLINK(\"\"x\"\")\";Otro;Egreso;-300,00;6000,00");
        assertThat(lines[2]).isEqualTo("15/08/2026 09:30;Sueldo 100% agosto;Depósito;Ingreso;2500,00;6300,00");
    }

    @Test
    void receiptShowsBothSidesOfATransfer() throws Exception {
        String melba = accessToken(TestData.CLIENT_EMAIL);
        mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(melba))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourceAccountNumber", "VIN001", "targetAccountNumber", "VIN999",
                                "amount", "100.00"))))
                .andExpect(status().isCreated());
        JsonNode latest = body(mvc.perform(get(url()).header(HttpHeaders.AUTHORIZATION, bearer(melba))).andReturn())
                .get("content").get(0);
        long id = latest.get("id").asLong();

        mvc.perform(get("/api/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(melba)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("VIN001"))
                .andExpect(jsonPath("$.accountHolder").value("Melba Morel"))
                .andExpect(jsonPath("$.category").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.counterparty").value("VIN999"))
                .andExpect(jsonPath("$.counterpartyHolder").value("Other C."))
                .andExpect(jsonPath("$.balanceAfter").value(4900.00));

        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        mvc.perform(get("/api/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        String admin = accessToken(TestData.ADMIN_EMAIL);
        mvc.perform(get("/api/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void anotherClientsMovementsAreNotFound() throws Exception {
        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        mvc.perform(get(url()).header(HttpHeaders.AUTHORIZATION, bearer(other))).andExpect(status().isNotFound());
        mvc.perform(get(url() + "/export").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get(url())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/transactions/999999").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
    }
}
