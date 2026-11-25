package com.example.racehorse_transport.policy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

// Giữ ngựa giao thất bại tại hub (B5), hàm thuần. Phí tính theo ngày, ngày lẻ tính tròn một ngày, tối thiểu một ngày
public final class StablingPolicy {

    private StablingPolicy() {}

    public static long days(Instant from, Instant to) {
        Duration elapsed = Duration.between(from, to);
        if (elapsed.isNegative() || elapsed.isZero()) return 1;
        long fullDays = elapsed.toDays();
        return elapsed.minusDays(fullDays).isZero() ? fullDays : fullDays + 1;
    }

    public static BigDecimal fee(Instant from, Instant to, int dailyFee) {
        return feeForDays(days(from, to), dailyFee);
    }

    public static BigDecimal feeForDays(long days, int dailyFee) {
        return BigDecimal.valueOf(dailyFee).multiply(BigDecimal.valueOf(days));
    }

    public static long holdDays(Instant chargedFrom, Instant through) {
        return through.isAfter(chargedFrom) ? days(chargedFrom, through) : 0;
    }

    public static BigDecimal depositCredit(BigDecimal fee, BigDecimal deposit, BigDecimal alreadyCredited) {
        return fee.min(deposit.subtract(alreadyCredited).max(BigDecimal.ZERO));
    }
}
