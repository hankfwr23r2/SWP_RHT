package com.example.racehorse_transport.policy;

import java.time.Duration;
import java.time.Instant;

// Con ngựa công ty đang giữ đã tới hạn khám lại chưa (NGHIEP_VU mục 7.2), hàm thuần.
// Trên xe: mỗi lần nghỉ, tối đa maxContinuousHours. Ở hub hoặc chuồng gửi: 2 lần mỗi ngày.
public final class CareCheckPolicy {

    static final int STAY_CHECKS_PER_DAY = 2;

    private CareCheckPolicy() {}

    // since: lúc nhận con ngựa; lastLog: lần ghi sức khỏe gần nhất, NULL khi chưa ghi
    public static Instant nextCheckAt(Instant since, Instant lastLog, boolean onVehicle, int maxContinuousHours) {
        Instant base = lastLog == null || lastLog.isBefore(since) ? since : lastLog;
        return base.plus(onVehicle ? Duration.ofHours(maxContinuousHours) : Duration.ofHours(24 / STAY_CHECKS_PER_DAY));
    }

    public static boolean overdue(Instant nextCheckAt, Instant now) {
        return now.isAfter(nextCheckAt);
    }
}
