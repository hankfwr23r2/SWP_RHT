package com.example.racehorse_transport.policy;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public final class BalanceHoldPolicy {
    public static final String RETURNED_AT_PICKUP = "Đã trả ngựa tại nơi đón do quá thời gian chờ thanh toán số dư.";
    public static final String CALL_ACTION_PREFIX = "BALANCE_HOLD_CALL_";
    public static final String CANCELLED_AFTER_HOLD = "Hết hạn chọn phương án sau khi ngựa được giữ tại hub.";
    public static final String WRITTEN_NOTICE_TITLE = "Thông báo bằng văn bản yêu cầu nhận lại ngựa";

    private BalanceHoldPolicy() {}

    public static boolean reached(Instant now, Instant base, long offset, ChronoUnit unit) {
        return !now.isBefore(base.plus(offset, unit));
    }

    public static boolean writtenNoticeMature(Instant now, Instant sentAt, int days) {
        return reached(now, sentAt, days, ChronoUnit.DAYS);
    }

    public static boolean waitingForGateway(Instant now, Instant cutoff, boolean pending, int graceMinutes) {
        return pending && !reached(now, cutoff, graceMinutes, ChronoUnit.MINUTES);
    }

    public static Duration delay(Instant planned, Instant proposed) {
        return proposed.isAfter(planned) ? Duration.between(planned, proposed) : Duration.ZERO;
    }

    public static long rideLimitSeconds(boolean direct, int maxDriveHours) {
        return TripSchedulePolicy.rideLimitSeconds(direct, new TripSchedulePolicy.Rules(0, 0, maxDriveHours, 0, 0));
    }
}
