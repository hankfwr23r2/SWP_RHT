package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.PaymentMethod;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentPolicyTest {
    @Test
    void sessionReferenceUsesAlphanumericsAndPreservesPaymentAndGatewayTime() {
        Instant created = Instant.parse("2027-01-15T10:00:00Z");
        String first = PaymentPolicy.reference(Long.MAX_VALUE, created, UUID.randomUUID());
        String second = PaymentPolicy.reference(Long.MAX_VALUE, created, UUID.randomUUID());
        assertThat(first).matches("[A-Za-z0-9]+").isNotEqualTo(second);
        assertThat(PaymentPolicy.parseReference(first)).isEqualTo(new PaymentPolicy.Session(Long.MAX_VALUE, created));
        assertThat(PaymentPolicy.parseReference("123")).isNull();
    }

    @Test
    void usesActualPaymentsAndRefundsInTheQuotationCurrency() {
        var quote = new Quotation();
        quote.setTotalAmount(new BigDecimal("8600"));
        quote.setCurrencyCode("VND");
        var deposit = payment(PaymentType.DEPOSIT, PaymentStatus.PAID, "3000", "VND");
        var pending = payment(PaymentType.BALANCE, PaymentStatus.PENDING, "5600", "VND");
        var refund = payment(PaymentType.REFUND, PaymentStatus.PAID, "500", "VND");
        assertThat(PaymentPolicy.outstanding(quote, List.of(deposit, pending, refund))).isEqualByComparingTo("6100");
        deposit.setCurrencyCode("USD");
        assertThatThrownBy(() -> PaymentPolicy.outstanding(quote, List.of(deposit)))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void depositAllowsLateConfirmationOnlyForFundsPaidBeforeTheDeadline() {
        var paidAt = Instant.parse("2027-01-15T10:00:00Z");
        var deadline = paidAt.plusSeconds(1);
        var booking = new Booking();
        booking.setStatus(BookingStatus.AWAITING_DEPOSIT);
        var quotation = new Quotation();
        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotation.setCurrencyCode("VND");
        var payment = payment(PaymentType.DEPOSIT, PaymentStatus.PENDING, "3000", "VND");
        payment.setQuotation(quotation);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setDueAt(deadline);
        var session = new PaymentPolicy.Session(1L, paidAt.minusSeconds(10));

        assertThat(PaymentPolicy.canApply(payment, booking, session, paidAt, deadline.plusSeconds(50),
                5, payment.getAmount())).isTrue();
        assertThat(PaymentPolicy.canApply(payment, booking, session, deadline, deadline.plusSeconds(50),
                5, payment.getAmount())).isFalse();
        assertThat(PaymentPolicy.canApply(payment, booking, session, paidAt, deadline.plusSeconds(300),
                5, payment.getAmount())).isFalse();
    }

    @Test
    void balanceCanApplyTheHigherSignedAmountFromBeforeTheQuoteWasReduced() {
        var now = Instant.parse("2027-01-15T10:00:00Z");
        var booking = new Booking();
        booking.setStatus(BookingStatus.CONFIRMED);
        var quotation = new Quotation();
        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotation.setCurrencyCode("VND");
        var payment = payment(PaymentType.BALANCE, PaymentStatus.PENDING, "3000", "VND");
        payment.setQuotation(quotation);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setDueAt(now.minusSeconds(5));
        var session = new PaymentPolicy.Session(1L, now.minusSeconds(120));
        var signedAmount = new BigDecimal("6000");

        assertThat(PaymentPolicy.canApply(payment, booking, session, now, now, 5, signedAmount)).isTrue();
        booking.setStatus(BookingStatus.CANCELLED);
        assertThat(PaymentPolicy.canApply(payment, booking, session, now, now, 5, signedAmount)).isFalse();
    }

    @Test
    void extraChargeCheckoutAndIpnRequireDeliveredBookingAndExactAmount() {
        Instant now = Instant.parse("2027-01-15T10:00:00Z");
        var booking = new Booking();
        booking.setStatus(BookingStatus.DELIVERED);
        var quotation = new Quotation();
        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotation.setCurrencyCode("VND");
        var extra = payment(PaymentType.EXTRA_CHARGE, PaymentStatus.PENDING, "3000", "VND");
        extra.setPaymentMethod(PaymentMethod.VNPAY);
        extra.setQuotation(quotation);
        var session = new PaymentPolicy.Session(1L, now.minusSeconds(120));

        assertThat(PaymentPolicy.isOpen(extra, booking, now)).isTrue();
        assertThat(PaymentPolicy.canApply(extra, booking, session, now, now, 5, new BigDecimal("3000"))).isTrue();
        assertThat(PaymentPolicy.canApply(extra, booking, session, now, now, 5, new BigDecimal("3100"))).isFalse();
        booking.setStatus(BookingStatus.COMPLETED);
        assertThat(PaymentPolicy.isOpen(extra, booking, now)).isFalse();
        assertThat(PaymentPolicy.canApply(extra, booking, session, now, now, 5, new BigDecimal("3000"))).isFalse();
    }

    @Test
    void extraChargeUsesApprovedCustomerSideAmountsAndRejectsLinkedCosts() {
        var booking = new Booking();
        booking.setId(1L);
        var cost = new CostItem();
        cost.setBooking(booking);
        cost.setPayerType(CostPayerType.CONSIGNEE);
        cost.setApprovedAt(Instant.EPOCH);
        cost.setCurrencyCode("VND");
        cost.setAmount(new BigDecimal("1500"));
        assertThat(PaymentPolicy.extraChargeAmount(1L, "VND", 1, List.of(cost))).isEqualByComparingTo("1500");
        cost.setExtraChargePayment(new Payment());
        assertThatThrownBy(() -> PaymentPolicy.extraChargeAmount(1L, "VND", 1, List.of(cost)))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void balanceBecomesOverdueAtTheEndOfTheGracePeriod() {
        var balance = payment(PaymentType.BALANCE, PaymentStatus.PENDING, "500", "VND");
        balance.setDueAt(Instant.EPOCH);
        assertThat(PaymentPolicy.isBalanceOverdue(List.of(balance), Instant.EPOCH.minusSeconds(1))).isFalse();
        assertThat(PaymentPolicy.isBalanceOverdue(List.of(balance), Instant.EPOCH)).isTrue();
        balance.setStatus(PaymentStatus.PAID);
        assertThat(PaymentPolicy.isBalanceOverdue(List.of(balance), Instant.EPOCH.plusSeconds(301))).isFalse();
    }

    private static Payment payment(PaymentType type, PaymentStatus status, String amount, String currency) {
        var payment = new Payment();
        payment.setPaymentType(type);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrencyCode(currency);
        return payment;
    }

    @Test
    void sessionOpenedBeforeDueWaitsOnlyTheGraceTimeAfterDue() {
        Instant due = Instant.parse("2026-12-01T01:30:00Z");
        var early = new PaymentPolicy.Session(1L, due.minusSeconds(1500));
        var atDue = new PaymentPolicy.Session(1L, due);
        assertThat(PaymentPolicy.awaitsGateway(early, due, due.plusSeconds(299), 5)).isTrue();
        assertThat(PaymentPolicy.awaitsGateway(early, due, due.plusSeconds(300), 5)).isFalse();
        assertThat(PaymentPolicy.awaitsGateway(atDue, due, due, 5)).isFalse();
        assertThat(PaymentPolicy.awaitsGateway(null, due, due, 5)).isFalse();

        var balance = payment(PaymentType.BALANCE, PaymentStatus.PENDING, "500", "VND");
        balance.setId(1L);
        balance.setDueAt(due);
        assertThat(PaymentPolicy.confirmationUntil(balance, List.of(early), 5)).isEqualTo(due.plusSeconds(300));
        assertThat(PaymentPolicy.confirmationUntil(balance, List.of(atDue), 5)).isNull();
        balance.setStatus(PaymentStatus.PAID);
        assertThat(PaymentPolicy.confirmationUntil(balance, List.of(early), 5)).isNull();
    }

    @Test
    void onlyTheTransactionThatPaidTheDebtCountsAsApplied() {
        Instant paidAt = Instant.parse("2026-12-01T01:00:00Z");
        var deposit = payment(PaymentType.DEPOSIT, PaymentStatus.PAID, "500", "VND");
        deposit.setPaidAt(paidAt);
        assertThat(PaymentPolicy.isAppliedBy(deposit, new BigDecimal("500.00"), paidAt)).isTrue();
        assertThat(PaymentPolicy.isAppliedBy(deposit, new BigDecimal("500"), paidAt.plusSeconds(1))).isFalse();
        assertThat(PaymentPolicy.isAppliedBy(deposit, new BigDecimal("500"), null)).isFalse();
        deposit.setStatus(PaymentStatus.CANCELLED);
        assertThat(PaymentPolicy.isAppliedBy(deposit, new BigDecimal("500"), paidAt)).isFalse();
    }
}
