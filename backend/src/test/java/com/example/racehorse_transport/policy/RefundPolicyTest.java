package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundPolicyTest {

    @Test
    void reservesBothPendingAndPaidRefundsAgainstTheirOriginalPayments() {
        var deposit = payment(1L, PaymentType.DEPOSIT, PaymentStatus.PAID, "4000", null);
        var balance = payment(2L, PaymentType.BALANCE, PaymentStatus.PAID, "6000", null);
        var pending = payment(3L, PaymentType.REFUND, PaymentStatus.PENDING, "1000", deposit);
        var paid = payment(4L, PaymentType.REFUND, PaymentStatus.PAID, "250", deposit);
        var cancelled = payment(5L, PaymentType.REFUND, PaymentStatus.CANCELLED, "500", balance);
        var rows = List.of(deposit, balance, pending, paid, cancelled);

        assertThat(PaymentPolicy.allocate(rows, "VND", new BigDecimal("8750")))
                .containsExactly(new PaymentPolicy.RefundAllocation(deposit, new BigDecimal("2750")),
                        new PaymentPolicy.RefundAllocation(balance, new BigDecimal("6000")));
        assertThatThrownBy(() -> PaymentPolicy.allocate(rows, "VND", new BigDecimal("8751")))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void refundsOnlyThePartOfExcessNotYetReserved() {
        var quotation = new Quotation();
        quotation.setTotalAmount(new BigDecimal("6000"));
        quotation.setCurrencyCode("VND");
        var deposit = payment(1L, PaymentType.DEPOSIT, PaymentStatus.PAID, "8000", null);
        var pending = payment(2L, PaymentType.REFUND, PaymentStatus.PENDING, "1100", deposit);
        var paid = payment(3L, PaymentType.REFUND, PaymentStatus.PAID, "400", deposit);

        assertThat(PaymentPolicy.unallocatedOverpayment(quotation, List.of(deposit, pending, paid)))
                .isEqualByComparingTo("500");
    }

    private static Payment payment(Long id, PaymentType type, PaymentStatus status, String amount, Payment original) {
        var row = new Payment();
        row.setId(id);
        row.setPaymentType(type);
        row.setStatus(status);
        row.setAmount(new BigDecimal(amount));
        row.setCurrencyCode("VND");
        row.setRefundedPayment(original);
        return row;
    }
}
