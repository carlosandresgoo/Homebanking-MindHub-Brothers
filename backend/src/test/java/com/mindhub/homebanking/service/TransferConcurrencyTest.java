package com.mindhub.homebanking.service;

import com.mindhub.homebanking.dto.TransferRequest;
import com.mindhub.homebanking.exception.BusinessRuleException;
import com.mindhub.homebanking.repository.AccountRepository;
import com.mindhub.homebanking.support.IntegrationTest;
import com.mindhub.homebanking.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Real concurrent transactions against the database: the row locks must prevent double spending. */
class TransferConcurrencyTest extends IntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void concurrentTransfersCannotSpendTheSameMoneyTwice() throws Exception {
        int attempts = 10;
        // 5000.00 available; each attempt moves 1000.00 -> exactly 5 may succeed.
        TransferRequest request = new TransferRequest("VIN001", "VIN999", new BigDecimal("1000.00"), null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                Callable<Boolean> task = () -> {
                    start.await();
                    try {
                        transferService.transfer(TestData.CLIENT_EMAIL, request);
                        return true;
                    } catch (BusinessRuleException insufficientFunds) {
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            start.countDown();

            int succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }

        assertThat(accountRepository.findById(ids.accountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("0.00");
        assertThat(accountRepository.findById(ids.otherAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("5001.00");
    }

    @Test
    void oppositeConcurrentTransfersDoNotDeadlock() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> {
                start.await();
                return transferService.transfer(TestData.CLIENT_EMAIL,
                        new TransferRequest("VIN001", "VIN999", new BigDecimal("10.00"), null));
            });
            Future<?> b = pool.submit(() -> {
                start.await();
                return transferService.transfer(TestData.OTHER_CLIENT_EMAIL,
                        new TransferRequest("VIN999", "VIN001", new BigDecimal("1.00"), null));
            });
            start.countDown();
            a.get();
            b.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(accountRepository.findById(ids.accountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("4991.00");
        assertThat(accountRepository.findById(ids.otherAccountId()).orElseThrow().getBalance())
                .isEqualByComparingTo("10.00");
    }
}
