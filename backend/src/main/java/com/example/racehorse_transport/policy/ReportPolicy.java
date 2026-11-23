package com.example.racehorse_transport.policy;

import java.time.Duration;
import java.time.Instant;

// Báo cáo vận hành, hàm thuần
public final class ReportPolicy {

    // Tới điểm dừng muộn hơn kế hoạch quá ngưỡng này thì tính là trễ
    public static final Duration LATE_TOLERANCE = Duration.ofMinutes(30);

    private ReportPolicy() {}

    public static boolean isLate(Instant plannedArrival, Instant actualArrival) {
        return plannedArrival != null && actualArrival != null && actualArrival.isAfter(plannedArrival.plus(LATE_TOLERANCE));
    }

    // Tỉ lệ điểm dừng đúng giờ trên số điểm đã tới; chưa tới điểm nào thì không có tỉ lệ
    public static Double onTimeRate(long arrived, long late) {
        return arrived == 0 ? null : (double) (arrived - late) / arrived;
    }
}
