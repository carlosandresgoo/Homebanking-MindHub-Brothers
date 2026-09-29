package com.mindhub.homebanking.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientLoanTest {

    private static Loan personal() {
        Loan loan = BeanFactory.newLoan();
        ReflectionTestUtils.setField(loan, "interestRate", new BigDecimal("0.2000"));
        return loan;
    }

    @Test
    void installmentsAddUpExactlyToTheTotalEvenWithRounding() {
        // 1000.01 * 1.2 = 1200.012 -> 1200.01; / 7 = 171.43 (rounded); the last one absorbs the remainder.
        ClientLoan loan = new ClientLoan(null, personal(), new BigDecimal("1000.01"), 7, LocalDateTime.now());
        assertThat(loan.getTotalDue()).isEqualByComparingTo("1200.01");

        BigDecimal paid = BigDecimal.ZERO;
        for (int i = 0; i < 7; i++) {
            paid = paid.add(loan.payInstallment());
        }

        assertThat(paid).isEqualByComparingTo("1200.01");
        assertThat(loan.getOutstanding()).isZero();
        assertThat(loan.isPaidOff()).isTrue();
        assertThat(loan.nextInstallment()).isZero();
        assertThatThrownBy(loan::payInstallment).isInstanceOf(IllegalStateException.class);
    }

    /** Loan has a protected constructor (JPA); tests build one reflectively. */
    private static final class BeanFactory {
        static Loan newLoan() {
            try {
                var constructor = Loan.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
