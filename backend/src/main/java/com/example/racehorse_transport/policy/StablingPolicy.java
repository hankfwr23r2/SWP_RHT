package com.example.racehorse_transport.policy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

// Giữ ngựa giao thất bại tại hub (B5), hàm thuần. Phí tính theo ngày, ngày lẻ tính tròn một ngày, tối thiểu một ngày
public final class StablingPolicy {

    private StablingPolicy() {}

    public static long days(Instant from, Instant to) {
        long minutes = Math.max(0, Duration.between(from, to).toMinutes());
        return Math.max(1, (minutes + Duration.ofDays(1).toMinutes() - 1) / Duration.ofDays(1).toMinutes());
    }

    public static BigDecimal fee(Instant from, Instant to, int dailyFee) {
        return BigDecimal.valueOf(dailyFee).multiply(BigDecimal.valueOf(days(from, to)));
    }
}
