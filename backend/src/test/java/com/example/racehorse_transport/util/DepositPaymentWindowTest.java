package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Báo giá có hiệu lực 24 giờ: cọc phải được trả trước hạn; kết quả VNPAY tới muộn vài phút vẫn được nhận nếu khách đã trả trước hạn.
class DepositPaymentWindowTest {

    private static final Instant SENT = Instant.parse("2026-11-10T08:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-11-11T08:00:00.700Z");

    private final Quotation quote = Quotation.builder().sentAt(SENT).expiresAt(EXPIRES).build();

    private Payment pending(Instant createdAt) {
        return Payment.builder().paymentType("DEPOSIT").paymentMethod("VNPAY").status("PENDING").paymentDate(createdAt).build();
    }

    @Test
    void expiryIsCutToTheSecondBecauseVnpayHasNoFractions() {
        assertThat(DepositPaymentWindow.expiresAt(quote)).isEqualTo(Instant.parse("2026-11-11T08:00:00Z"));
    }

    @Test
    void graceStartsAfterExpiryAndNeverGoesNegative() {
        assertThat(DepositPaymentWindow.confirmationUntil(quote, 5)).isEqualTo(Instant.parse("2026-11-11T08:05:00Z"));
        assertThat(DepositPaymentWindow.confirmationUntil(quote, -3)).isEqualTo(Instant.parse("2026-11-11T08:00:00Z"));
    }

    @Test
    void aPaymentSessionBelongsToTheQuoteOnlyWhenCreatedWhileItWasValid() {
        assertThat(DepositPaymentWindow.belongsToQuote(pending(SENT), quote)).isTrue();
        assertThat(DepositPaymentWindow.belongsToQuote(pending(SENT.minusSeconds(1)), quote)).isFalse();
        assertThat(DepositPaymentWindow.belongsToQuote(pending(Instant.parse("2026-11-11T08:00:00Z")), quote)).isFalse();
        assertThat(DepositPaymentWindow.belongsToQuote(pending(null), quote)).isFalse();
    }

    @Test
    void onlyAPendingVnpayDepositOfThisQuoteCountsAsPending() {
        Payment other = Payment.builder().paymentType("BALANCE").paymentMethod("VNPAY").status("PENDING").paymentDate(SENT.plusSeconds(60)).build();
        Payment done = Payment.builder().paymentType("DEPOSIT").paymentMethod("VNPAY").status("PAID").paymentDate(SENT.plusSeconds(60)).build();

        assertThat(DepositPaymentWindow.hasPendingDeposit(List.of(other, done), quote)).isFalse();
        assertThat(DepositPaymentWindow.hasPendingDeposit(List.of(other, done, pending(SENT.plusSeconds(60))), quote)).isTrue();
    }

    @Test
    void depositPaidBeforeExpiryIsConfirmedEvenIfTheResultArrivesInTheGrace() {
        Payment payment = pending(SENT.plusSeconds(600));
        Instant paidAt = Instant.parse("2026-11-11T07:59:50Z");
        Instant received = Instant.parse("2026-11-11T08:03:00Z");

        assertThat(DepositPaymentWindow.canConfirm(quote, payment, paidAt, received, 5)).isTrue();
    }

    @Test
    void depositPaidAfterExpiryIsNotConfirmed() {
        Payment payment = pending(SENT.plusSeconds(600));
        Instant paidAt = Instant.parse("2026-11-11T08:00:00Z");

        assertThat(DepositPaymentWindow.canConfirm(quote, payment, paidAt, paidAt.plusSeconds(10), 5)).isFalse();
    }

    @Test
    void resultArrivingAfterTheGraceIsNotConfirmed() {
        Payment payment = pending(SENT.plusSeconds(600));
        Instant paidAt = Instant.parse("2026-11-11T07:59:50Z");

        assertThat(DepositPaymentWindow.canConfirm(quote, payment, paidAt, Instant.parse("2026-11-11T08:05:00Z"), 5)).isFalse();
    }

    @Test
    void paymentTimeBeforeTheSessionWasCreatedOrInTheFutureIsRefused() {
        Payment payment = pending(SENT.plusSeconds(600));
        Instant received = Instant.parse("2026-11-10T09:00:00Z");

        assertThat(DepositPaymentWindow.canConfirm(quote, payment, SENT.plusSeconds(599), received, 5)).isFalse();
        assertThat(DepositPaymentWindow.canConfirm(quote, payment, received.plusSeconds(1), received, 5)).isFalse();
        assertThat(DepositPaymentWindow.canConfirm(quote, payment, null, received, 5)).isFalse();
    }
}
