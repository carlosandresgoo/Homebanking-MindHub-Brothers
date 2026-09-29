package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class IdempotencyTest extends IntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    private MvcResult transfer(String token, String key, String amount) throws Exception {
        var request = post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sourceAccountNumber", "VIN001", "targetAccountNumber", "VIN999",
                        "amount", amount)));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn();
    }

    private java.math.BigDecimal balance(String token, long accountId) throws Exception {
        return body(mvc.perform(get("/api/accounts/" + accountId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn()).get("balance").decimalValue();
    }

    @Test
    void retryingWithTheSameKeyReturnsTheOriginalReceiptWithoutTransferringAgain() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        String key = UUID.randomUUID().toString();

        MvcResult first = transfer(token, key, "100.00");
        MvcResult retry = transfer(token, key, "100.00");

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(body(retry)).isEqualTo(body(first));
        assertThat(balance(token, ids.accountId())).isEqualByComparingTo("4900.00");
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        String key = UUID.randomUUID().toString();
        transfer(token, key, "100.00");

        MvcResult different = transfer(token, key, "200.00");

        assertThat(different.getResponse().getStatus()).isEqualTo(422);
        assertThat(balance(token, ids.accountId())).isEqualByComparingTo("4900.00");
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        String key = UUID.randomUUID().toString();
        String melba = accessToken(TestData.CLIENT_EMAIL);
        String other = accessToken(TestData.OTHER_CLIENT_EMAIL);
        transfer(melba, key, "100.00");

        MvcResult othersTransfer = mvc.perform(post("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(other))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("sourceAccountNumber", "VIN999", "targetAccountNumber", "VIN001",
                        "amount", "1.00")))).andReturn();

        assertThat(othersTransfer.getResponse().getStatus()).isEqualTo(201);
        assertThat(othersTransfer.getResponse().getHeader("Idempotent-Replayed")).isNull();
    }

    @Test
    void withoutAKeyEachRequestRuns() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        transfer(token, null, "100.00");
        transfer(token, null, "100.00");
        assertThat(balance(token, ids.accountId())).isEqualByComparingTo("4800.00");
    }

    @Test
    void malformedKeysAre400() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        assertThat(transfer(token, "short", "1.00").getResponse().getStatus()).isEqualTo(400);
        assertThat(transfer(token, "bad key with spaces!", "1.00").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void loanPaymentsAndAccountOpeningAreIdempotentToo() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        long loanId = body(mvc.perform(post("/api/loans").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("loanId", 2, "amount", "600.00", "payments", 6, "accountNumber", "VIN001"))))
                .andReturn()).get("id").asLong();

        String payKey = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/clients/current/loans/" + loanId + "/payments")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)).header("Idempotency-Key", payKey)
                    .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("accountNumber", "VIN001"))));
        }
        int paymentsMade = body(mvc.perform(get("/api/clients/current/loans")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn()).get(0).get("paymentsMade").asInt();
        assertThat(paymentsMade).isEqualTo(1);

        String openKey = UUID.randomUUID().toString();
        String first = mvc.perform(post("/api/clients/current/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", openKey)).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/clients/current/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", openKey)).andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(body(mvc.perform(get("/api/clients/current/accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn())).hasSize(2);
    }

    @Test
    void concurrentRequestsWithTheSameKeyTransferOnlyOnce() throws Exception {
        String token = accessToken(TestData.CLIENT_EMAIL);
        String key = UUID.randomUUID().toString();
        int attempts = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<Integer>> statuses = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                Callable<Integer> task = () -> {
                    start.await();
                    return transfer(token, key, "100.00").getResponse().getStatus();
                };
                statuses.add(pool.submit(task));
            }
            start.countDown();
            Map<Integer, Integer> byStatus = new HashMap<>();
            for (Future<Integer> status : statuses) {
                byStatus.merge(status.get(), 1, Integer::sum);
            }
            // Every response is either the transfer (or its replay) or "already being processed".
            assertThat(byStatus.keySet()).isSubsetOf(201, 409);
        } finally {
            pool.shutdownNow();
        }

        assertThat(accountRepository.findById(ids.accountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("4900.00");
    }
}
