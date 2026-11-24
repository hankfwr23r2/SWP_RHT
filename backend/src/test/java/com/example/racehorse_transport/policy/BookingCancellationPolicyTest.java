package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.BillableService;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.ScheduleCancellation;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.util.List;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.Breakdown;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.Outcome;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BookingCancellationPolicyTest {

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"PENDING_INTAKE", "UNDER_REVIEW", "NEEDS_RESUBMISSION", "QUOTING",
            "AWAITING_DEPOSIT", "QUOTE_EXPIRED"})
    void withdrawingBeforeDepositIsFree(BookingStatus status) {
        assertThat(BookingCancellationPolicy.byCustomer(status, false, false)).isEqualTo(Outcome.FREE);
        assertThat(BookingCancellationPolicy.byCustomer(status, true, false)).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(BookingCancellationPolicy.companyCanCancel(status)).isTrue();
    }

    @Test
    void confirmedBookingForfeitsDepositUntilPaidInFull() {
        assertThat(BookingCancellationPolicy.byCustomer(BookingStatus.CONFIRMED, true, false)).isEqualTo(Outcome.FORFEIT_DEPOSIT);
        assertThat(BookingCancellationPolicy.byCustomer(BookingStatus.CONFIRMED, true, true)).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(BookingCancellationPolicy.companyCanCancel(BookingStatus.CONFIRMED)).isTrue();
    }

    @Test
    void paidBookingAwaitingScheduleChangeRequiresRecordedCause() {
        assertThat(BookingCancellationPolicy.byCustomer(BookingStatus.SCHEDULE_CHANGE_REQUIRED, true, true)).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(BookingCancellationPolicy.scheduleCancellation(ScheduleChangeCause.COMPANY, 0, 2).outcome())
                .isEqualTo(Outcome.FREE);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"IN_OPERATION", "DELIVERED", "COMPLETED", "CANCELLED", "REJECTED"})
    void nobodyCancelsAfterPickupOrWhenClosed(BookingStatus status) {
        assertThat(BookingCancellationPolicy.byCustomer(status, true, true)).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(BookingCancellationPolicy.companyCanCancel(status)).isFalse();
    }

    @Test
    void splitsMoney() {
        assertSplit(BookingCancellationPolicy.refundAll(new BigDecimal("3700000")), "3700000", "0", "0");
        assertSplit(BookingCancellationPolicy.forfeit(new BigDecimal("3700000"), new BigDecimal("1000000")), "0", "1000000", "2700000");
        assertSplit(BookingCancellationPolicy.forfeit(new BigDecimal("500000"), new BigDecimal("1000000")), "0", "500000", "0");
    }

    @Test
    void customerAndAuthoritySeparateTransportFromUnspentQuoteLines() {
        var quotation = new Quotation();
        quotation.setDepositAmount(new BigDecimal("4000"));
        quotation.setCurrencyCode("VND");
        var deposit = new Payment();
        deposit.setPaymentType(PaymentType.DEPOSIT);
        deposit.setStatus(PaymentStatus.PAID);
        deposit.setAmount(new BigDecimal("4000"));
        deposit.setCurrencyCode("VND");

        var passThrough = line(1L, "1000", true, null);
        var vet = line(2L, "500", false, "VET_CHECK");
        var transport = line(3L, "9000", false, null);
        var lines = List.of(passThrough, vet, transport);

        var customer = BookingCancellationPolicy.amounts(Outcome.FORFEIT_DEPOSIT, false, quotation, lines, List.of(deposit));
        var authority = BookingCancellationPolicy.amounts(Outcome.FORFEIT_DEPOSIT, true, quotation, lines, List.of(deposit));
        assertSplit(customer, "0", "1500", "2500");
        assertSplit(authority, "2500", "1500", "0");
        assertSplit(BookingCancellationPolicy.includeUnspentLines(customer, lines, List.of(2L)), "500", "1000", "2500");
        assertThatThrownBy(() -> BookingCancellationPolicy.includeUnspentLines(customer, lines, List.of(3L)))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> BookingCancellationPolicy.includeUnspentLines(customer, lines, List.of(1L, 1L)))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void scheduledCancellationUsesCauseAndAuthorityMissThreshold() {
        assertThat(BookingCancellationPolicy.scheduleCancellation(ScheduleChangeCause.CUSTOMER, 5, 2))
                .isEqualTo(new ScheduleCancellation(Outcome.FORFEIT_DEPOSIT, false));
        assertThat(BookingCancellationPolicy.scheduleCancellation(ScheduleChangeCause.COMPANY, 0, 2))
                .isEqualTo(new ScheduleCancellation(Outcome.FREE, false));
        assertThat(BookingCancellationPolicy.scheduleCancellation(ScheduleChangeCause.AUTHORITY, 1, 2))
                .isEqualTo(new ScheduleCancellation(Outcome.FORFEIT_DEPOSIT, false));
        assertThat(BookingCancellationPolicy.scheduleCancellation(ScheduleChangeCause.AUTHORITY, 2, 2))
                .isEqualTo(new ScheduleCancellation(Outcome.FORFEIT_DEPOSIT, true));
        assertThat(BookingCancellationPolicy.scheduleCancellation(null, 0, 2))
                .isEqualTo(new ScheduleCancellation(Outcome.NOT_ALLOWED, false));
    }

    @Test
    void blocksRefundWhenLineAlreadyHasARefundPayment() {
        var refundedLine = line(1L, "1000", true, null);
        refundedLine.setRefundPayment(new Payment());
        assertThat(BookingCancellationPolicy.canRefundLine(refundedLine)).isFalse();
        assertThatThrownBy(() -> BookingCancellationPolicy.includeUnspentLines(
                new Breakdown(BigDecimal.ZERO, new BigDecimal("2000"), BigDecimal.ZERO), List.of(refundedLine), List.of(1L)))
                .isInstanceOf(BookingRuleException.class);
    }

    private static QuotationLine line(Long id, String amount, boolean passThrough, String service) {
        var line = new QuotationLine();
        line.setId(id);
        line.setAmount(new BigDecimal(amount));
        line.setPassThrough(passThrough);
        if (service != null) {
            var billable = new BillableService();
            billable.setCode(service);
            line.setBillableService(billable);
        }
        return line;
    }

    private static void assertSplit(Breakdown actual, String refundable, String reviewable, String forfeited) {
        assertThat(actual.refundable()).isEqualByComparingTo(refundable);
        assertThat(actual.reviewable()).isEqualByComparingTo(reviewable);
        assertThat(actual.forfeited()).isEqualByComparingTo(forfeited);
    }
}
