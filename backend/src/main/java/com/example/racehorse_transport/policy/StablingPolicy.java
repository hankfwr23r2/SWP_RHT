package com.example.racehorse_transport.policy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Giữ ngựa tại hub do khách (PHUONG_AN_6_LUONG mục 13.2), hàm thuần.
// Phí = số lần đủ 24 giờ cộng dồn; phần lẻ không tính, tối đa maxBlocks lần.
public final class StablingPolicy {
    private static final Duration BLOCK = Duration.ofHours(24);

    private StablingPolicy() {}

    // Một khoảng đồng hồ chạy; to = null là đang chạy
    public record Run(Instant from, Instant to) {}

    // Giờ hub nhận, trả ngựa; mốc rơi ngoài giờ thì dời tới morning của lần mở cửa kế tiếp
    public record Hours(LocalTime open, LocalTime close, LocalTime morning, ZoneId zone) {}

    public static long blocks(List<Run> runs, Instant through, Hours hours, int maxBlocks) {
        long count = 0;
        while (count < maxBlocks) {
            var mark = markAt(runs, count + 1, hours);
            if (mark.isEmpty() || mark.get().isAfter(through)) return count;
            count++;
        }
        return count;
    }

    // Mốc tính tiền kế tiếp nếu ngựa vẫn ở hub; trống khi đồng hồ đã dừng hoặc đã tới trần
    public static Optional<Instant> nextMark(List<Run> runs, Instant now, Hours hours, int maxBlocks) {
        long charged = blocks(runs, now, hours, maxBlocks);
        return charged >= maxBlocks ? Optional.empty() : markAt(runs, charged + 1, hours);
    }

    private static Optional<Instant> markAt(List<Run> runs, long block, Hours hours) {
        Duration left = BLOCK.multipliedBy(block);
        for (Run run : runs.stream().sorted(Comparator.comparing(Run::from)).toList()) {
            if (run.to() == null) return Optional.of(shift(run.from().plus(left), hours));
            Duration length = Duration.between(run.from(), run.to());
            if (length.isNegative()) continue;
            if (length.compareTo(left) >= 0) return Optional.of(shift(run.from().plus(left), hours));
            left = left.minus(length);
        }
        return Optional.empty();
    }

    private static Instant shift(Instant mark, Hours hours) {
        var local = mark.atZone(hours.zone());
        LocalTime time = local.toLocalTime();
        if (!time.isBefore(hours.open()) && !time.isAfter(hours.close())) return mark;
        var day = time.isBefore(hours.open()) ? local.toLocalDate() : local.toLocalDate().plusDays(1);
        return day.atTime(hours.morning()).atZone(hours.zone()).toInstant();
    }

    public static BigDecimal fee(long blocks, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(blocks));
    }

    public static BigDecimal depositCredit(BigDecimal fee, BigDecimal deposit, BigDecimal alreadyCredited) {
        return fee.min(deposit.subtract(alreadyCredited).max(BigDecimal.ZERO));
    }
}
