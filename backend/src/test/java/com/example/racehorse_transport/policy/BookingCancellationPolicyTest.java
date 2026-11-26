package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
        var lines = List.of(line(1L, "1000000", true, null));
        assertSplit(cancel(false, "3700000", lines, deposit("3700000")), "0", "0", "3700000");
        // Mới trả một phần nhỏ hơn khoản thu hộ: không giữ quá số đã trả
        assertSplit(cancel(false, "3700000", lines, deposit("500000")), "0", "0", "500000");
        assertSplit(cancel(true, "3700000", lines, deposit("500000")), "0", "0", "500000");
    }

    @Test
    void customerLosesWholeDepositAndAuthorityRefundsOnlyTransport() {
        var lines = List.of(line(1L, "1000", true, null), line(2L, "500", false, "VET_CHECK"), line(3L, "9000", false, null));

        assertSplit(cancel(false, "4000", lines, deposit("4000")), "0", "0", "4000");
        // Phí thú y xử lý như cước dịch vụ, chỉ khoản thu hộ bị giữ
        assertSplit(cancel(true, "4000", lines, deposit("4000")), "3000", "0", "1000");
        assertSplit(BookingCancellationPolicy.amounts(Outcome.FREE, false, quotation("4000"), lines, List.of(deposit("4000"))),
                "4000", "0", "0");
    }

    @Test
    void negativeOrPlainLinesDoNotChangeForfeitedAmount() {
        var lines = List.of(line(1L, "1000", true, null), line(2L, "-300", true, null), line(3L, "9000", false, "ESCORT"));

        assertSplit(cancel(true, "4000", lines, deposit("4000")), "3000", "0", "1000");
    }

    @Test
    void fullyPaidQuoteRefundsTheRestAfterForfeitedPart() {
        var lines = List.of(line(1L, "1000", true, null), line(2L, "500", false, "VET_CHECK"), line(3L, "8500", false, null));
        var balance = payment(PaymentType.BALANCE, PaymentStatus.PAID, "6000");

        assertSplit(cancel(false, "4000", lines, deposit("4000"), balance), "6000", "0", "4000");
        assertSplit(cancel(true, "4000", lines, deposit("4000"), balance), "9000", "0", "1000");
        // Cọc bằng tổng báo giá: khách hủy thì mất toàn bộ
        assertSplit(cancel(false, "10000", lines, deposit("10000")), "0", "0", "10000");
    }

    @Test
    void earlierRefundsReduceRefundableBeforeForfeitedPart() {
        var lines = List.of(line(1L, "1000", true, null));
        var pending = payment(PaymentType.REFUND, PaymentStatus.PENDING, "700");
        var paid = payment(PaymentType.REFUND, PaymentStatus.PAID, "300");

        assertSplit(cancel(true, "4000", lines, deposit("4000"), pending, paid), "2000", "0", "1000");
        assertSplit(cancel(false, "3000", lines, deposit("4000"), pending, paid), "0", "0", "3000");
        // Hoàn trước đã vượt phần được hoàn: không âm
        assertSplit(cancel(true, "4000", lines, deposit("4000"), payment(PaymentType.REFUND, PaymentStatus.PAID, "3500")),
                "0", "0", "500");
        assertThatThrownBy(() -> cancel(true, "4000", lines, deposit("4000"),
                payment(PaymentType.REFUND, PaymentStatus.PAID, "4001"))).isInstanceOf(BookingRuleException.class);
    }

    @Test
    void rejectsEveryLineSelectedForRefund() {
        assertThatCode(() -> BookingCancellationPolicy.requireNoLineRefund(List.of())).doesNotThrowAnyException();
        for (var selected : List.of(List.of(1L), List.of(2L), List.of(1L, 1L))) {
            assertThatThrownBy(() -> BookingCancellationPolicy.requireNoLineRefund(selected))
                    .isInstanceOf(BookingRuleException.class)
                    .hasMessageContaining("không được hoàn riêng khi hủy đơn");
        }
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
    void lineRefundedUnderOldRuleIsNotRefundedAgain() {
        var refundedLine = line(1L, "1000", true, null);
        refundedLine.setRefundPayment(new Payment());
        var refund = payment(PaymentType.REFUND, PaymentStatus.PAID, "1000");

        assertSplit(cancel(false, "4000", List.of(refundedLine), deposit("4000"), refund), "0", "0", "3000");
        assertThatThrownBy(() -> BookingCancellationPolicy.requireNoLineRefund(List.of(refundedLine.getId())))
                .isInstanceOf(BookingRuleException.class);
    }

    private static Breakdown cancel(boolean refundTransport, String depositAmount, List<QuotationLine> lines, Payment... payments) {
        return BookingCancellationPolicy.amounts(Outcome.FORFEIT_DEPOSIT, refundTransport, quotation(depositAmount),
                lines, List.of(payments));
    }

    private static Quotation quotation(String depositAmount) {
        var quotation = new Quotation();
        quotation.setDepositAmount(new BigDecimal(depositAmount));
        quotation.setCurrencyCode("VND");
        return quotation;
    }

    private static Payment deposit(String amount) {
        return payment(PaymentType.DEPOSIT, PaymentStatus.PAID, amount);
    }

    private static Payment payment(PaymentType type, PaymentStatus status, String amount) {
        var payment = new Payment();
        payment.setPaymentType(type);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrencyCode("VND");
        return payment;
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
