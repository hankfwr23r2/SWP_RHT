package com.example.racehorse_transport.policy;

import java.time.Instant;
import java.util.Set;

// Bộ lọc trang nhật ký hệ thống, hàm thuần
public final class SystemLogQueryPolicy {

    public static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);

    private SystemLogQueryPolicy() {}

    public static void requireValid(Instant from, Instant to, int page, int size) {
        if (!from.isBefore(to)) throw new IllegalArgumentException("Ngày bắt đầu phải trước ngày kết thúc.");
        if (page < 0) throw new IllegalArgumentException("Số trang không hợp lệ.");
        if (!PAGE_SIZES.contains(size)) throw new IllegalArgumentException("Mỗi trang chỉ được 20, 50 hoặc 100 dòng.");
    }

    // Mã bản ghi đích là số id; nhập chữ thì không khớp dòng nào
    public static Long targetId(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            return Long.valueOf(key.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mã bản ghi phải là số.");
        }
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
