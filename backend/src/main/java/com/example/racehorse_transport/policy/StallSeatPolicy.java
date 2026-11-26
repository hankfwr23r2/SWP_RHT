package com.example.racehorse_transport.policy;

import java.util.List;
import java.util.Map;

public final class StallSeatPolicy {

    private StallSeatPolicy() {}

    public static int seats(String code) {
        if ("STANDARD".equals(code)) {
            return 1;
        }
        if ("WIDE".equals(code)) {
            return 2;
        }
        throw new IllegalArgumentException("Loại khoang không hợp lệ: " + code);
    }

    public static int total(List<String> codes) {
        return codes.stream().mapToInt(StallSeatPolicy::seats).reduce(0, Math::addExact);
    }

    public static int total(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .mapToInt(entry -> Math.multiplyExact(seats(entry.getKey()), entry.getValue()))
                .reduce(0, Math::addExact);
    }
}
