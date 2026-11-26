package com.example.racehorse_transport.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

// Đồng hồ lệch so với giờ thật một khoảng chỉnh được lúc chạy. Chỉ dùng ở chế độ chạy thử.
public final class OffsetClock extends Clock {

    private final Clock base;
    private volatile Duration offset;

    public OffsetClock(Clock base, Duration offset) {
        this.base = base;
        this.offset = offset;
    }

    public Duration offset() {
        return offset;
    }

    public void setOffset(Duration value) {
        offset = value;
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new OffsetClock(base.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset);
    }
}
