package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BillStatementPolicyTest {

    private static Payment payment(PaymentType type, PaymentStatus status, long amount) {
        Payment payment = new Payment();
        payment.setPaymentType(type);
        payment.setStatus(status);
        payment.setAmount(BigDecimal.valueOf(amount));
        return payment;
    }

    @Test
    void fullyPaidOrderOwesNothing() {
        var statement = BillStatementPolicy.statement(BigDecimal.valueOf(10_000_000), List.of(
                payment(PaymentType.DEPOSIT, PaymentStatus.PAID, 3_000_000),
                payment(PaymentType.BALANCE, PaymentStatus.PAID, 7_000_000),
                payment(PaymentType.EXTRA_CHARGE, PaymentStatus.PAID, 500_000)));

        assertThat(statement.paid()).isEqualByComparingTo("10500000");
        assertThat(statement.extraCharges()).isEqualByComparingTo("500000");
        assertThat(statement.refunded()).isZero();
        assertThat(statement.outstanding()).isZero();
    }

    @Test
    void unpaidAndFailedPaymentsAreNotCountedAsPaid() {
        var statement = BillStatementPolicy.statement(BigDecimal.valueOf(10_000_000), List.of(
                payment(PaymentType.DEPOSIT, PaymentStatus.PAID, 3_000_000),
                payment(PaymentType.BALANCE, PaymentStatus.PENDING, 7_000_000),
                payment(PaymentType.EXTRA_CHARGE, PaymentStatus.FAILED, 400_000)));

        assertThat(statement.paid()).isEqualByComparingTo("3000000");
        assertThat(statement.extraCharges()).isZero();
        assertThat(statement.outstanding()).isEqualByComparingTo("7000000");
    }

    @Test
    void cancellationKeepsWhatIsNotRefunded() {
        var statement = BillStatementPolicy.statement(BigDecimal.valueOf(10_000_000), List.of(
                payment(PaymentType.DEPOSIT, PaymentStatus.PAID, 3_000_000),
                payment(PaymentType.REFUND, PaymentStatus.PENDING, 2_800_000)));

        assertThat(statement.refundPending()).isEqualByComparingTo("2800000");
        assertThat(statement.retained()).isEqualByComparingTo("200000");
    }

    @Test
    void balanceIsTotalMinusDeposit() {
        assertThat(BillStatementPolicy.balance(BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(3_200_000)))
                .isEqualByComparingTo("6800000");
        assertThat(BillStatementPolicy.balance(null, null)).isZero();
    }
}
