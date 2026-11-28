package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DepositPaymentWindowPolicyTest {

    private static final Instant EXPIRES = Instant.parse("2026-12-01T10:00:00.900Z");
    private static final Instant CREATED = Instant.parse("2026-12-01T09:50:00.700Z");

    private static boolean confirm(String paidAt, String receivedAt) {
        return DepositPaymentWindowPolicy.canConfirm(EXPIRES, CREATED, paidAt == null ? null : Instant.parse(paidAt),
                Instant.parse(receivedAt), 5);
    }

    @Test
    void acceptsPaymentBeforeDeadlineEvenWhenResultArrivesWithinGrace() {
        assertThat(confirm("2026-12-01T09:59:59Z", "2026-12-01T10:00:01Z")).isTrue();
        assertThat(confirm("2026-12-01T09:59:59Z", "2026-12-01T10:04:59Z")).isTrue();
        // Cổng chỉ chính xác tới giây, trùng giây tạo phiên vẫn hợp lệ
        assertThat(confirm("2026-12-01T09:50:00Z", "2026-12-01T09:51:00Z")).isTrue();
    }

    @Test
    void rejectsLatePaymentLateResultAndImpossibleTimes() {
        assertThat(confirm("2026-12-01T10:00:00Z", "2026-12-01T10:00:01Z")).isFalse();
        assertThat(confirm("2026-12-01T09:59:59Z", "2026-12-01T10:05:00Z")).isFalse();
        assertThat(confirm("2026-12-01T09:49:59Z", "2026-12-01T09:51:00Z")).isFalse();
        assertThat(confirm("2026-12-01T09:52:00Z", "2026-12-01T09:51:00Z")).isFalse();
        assertThat(confirm(null, "2026-12-01T09:51:00Z")).isFalse();
    }

    @Test
    void negativeGraceMeansNoWaiting() {
        assertThat(DepositPaymentWindowPolicy.confirmationUntil(EXPIRES, -3)).isEqualTo(Instant.parse("2026-12-01T10:00:00Z"));
    }
}
