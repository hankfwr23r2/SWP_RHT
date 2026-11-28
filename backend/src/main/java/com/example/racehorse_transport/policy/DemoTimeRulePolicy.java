package com.example.racehorse_transport.policy;

import java.util.Map;
import java.util.OptionalInt;

// Giá trị tham số thời gian khi bật demo bỏ kiểm tra giờ (app.demo.skip-time-rules). Hàm thuần.
// Chỉ gồm các mốc chặn thao tác; mốc tính tiền, hạn báo giá, hạn trả số dư giữ nguyên.
public final class DemoTimeRulePolicy {

    // Quyết định chạy chuyến: cửa sổ đủ rộng để quyết định ngay sau khi chuyến có đơn
    public static final int DECISION_HOURS = 100_000;

    private static final Map<String, Integer> OVERRIDES = Map.ofEntries(
            Map.entry("HUB_CHECKIN_HOURS", 0),
            Map.entry("MIN_REST_MINUTES", 0),
            Map.entry("HANDOVER_FREE_WAIT_MINUTES", 0),
            Map.entry("INTERMEDIATE_WAIT_MINUTES", 0),
            Map.entry("PICKUP_MISS_WAIT_MINUTES", 0),
            Map.entry("EARLY_START_MINUTES", 100_000),
            Map.entry("PICKUP_WINDOW_START_HOUR", 0),
            Map.entry("PICKUP_WINDOW_END_HOUR", 24),
            Map.entry("DELIVERY_WINDOW_START_HOUR", 0),
            Map.entry("DELIVERY_WINDOW_END_HOUR", 24));

    private DemoTimeRulePolicy() {
    }

    // Giờ chốt nhận đơn khi demo: một phút trước giờ xuất phát
    public static java.time.Instant bookingCutoffAt(java.time.Instant plannedDepartureAt) {
        return plannedDepartureAt.minus(java.time.Duration.ofMinutes(1));
    }

    public static OptionalInt override(String key) {
        Integer value = OVERRIDES.get(key);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
