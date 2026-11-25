package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerDebtPolicyTest {
    private static final Instant ISSUED = Instant.parse("2026-11-01T01:00:00Z");
    private static final Instant DUE = ISSUED.plus(Duration.ofDays(7));
    private static final BigDecimal MIN = new BigDecimal("200000");

    private static Payment debt(String amount) {
        var payment = new Payment();
        payment.setPaymentType(PaymentType.EXTRA_CHARGE);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal(amount));
        payment.setIssuedAt(ISSUED);
        payment.setDueAt(DUE);
        return payment;
    }

    @Test
    void smallDebtOnlyRemindsAndLargeOverdueDebtBlocks() {
        var late = DUE.plusSeconds(1);
        assertThat(CustomerDebtPolicy.blocksSubmission(List.of(debt("199999")), late, MIN)).isFalse();
        assertThat(CustomerDebtPolicy.blocksSubmission(List.of(debt("200000")), late, MIN)).isTrue();
        assertThat(CustomerDebtPolicy.blocksSubmission(List.of(debt("200000")), DUE, MIN)).isFalse();
        assertThat(CustomerDebtPolicy.blocksSubmission(List.of(), late, BigDecimal.ONE)).isFalse();
    }

    @Test
    void disputePausesTheDebtAndShiftsTheDueDate() {
        var payment = debt("500000");
        var disputed = DUE.minus(Duration.ofDays(1));
        payment.setDisputedAt(disputed);
        assertThat(CustomerDebtPolicy.isOverdue(payment, DUE.plus(Duration.ofDays(9)))).isFalse();
        assertThat(CustomerDebtPolicy.dueAfterDispute(payment, disputed.plus(Duration.ofDays(5)))).isEqualTo(DUE.plus(Duration.ofDays(5)));
        assertThatThrownBy(() -> CustomerDebtPolicy.requireDisputable(payment)).isInstanceOf(BookingRuleException.class);
    }

    @Test
    void extensionIsOnceAndLimited() {
        var payment = debt("500000");
        CustomerDebtPolicy.requireExtendable(payment, 14, 14);
        assertThatThrownBy(() -> CustomerDebtPolicy.requireExtendable(payment, 15, 14)).isInstanceOf(BookingRuleException.class);
        payment.setExtendedAt(DUE);
        assertThatThrownBy(() -> CustomerDebtPolicy.requireExtendable(payment, 1, 14)).isInstanceOf(BookingRuleException.class);
    }

    @Test
    void onlyBadDebtCanBeWrittenOffAndTheBlockStaysUntilReleased() {
        var payment = debt("500000");
        var bad = ISSUED.plus(Duration.ofDays(30));
        assertThatThrownBy(() -> CustomerDebtPolicy.requireWriteOff(payment, bad.minusSeconds(1), 30)).isInstanceOf(BookingRuleException.class);
        CustomerDebtPolicy.requireWriteOff(payment, bad, 30);
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setWrittenOffAt(bad);
        assertThat(CustomerDebtPolicy.blocksSubmission(List.of(payment), bad, MIN)).isTrue();
        payment.setBlockReleasedAt(bad);
        assertThat(CustomerDebtPolicy.blocksSubmission(List.of(payment), bad, MIN)).isFalse();
    }
}
