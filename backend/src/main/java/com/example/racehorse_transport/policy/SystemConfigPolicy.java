package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.exception.SystemConfigRuleException;
import java.util.Set;

public final class SystemConfigPolicy {

    private static final Set<String> INTEGER_UNITS = Set.of("MINUTE", "HOUR", "DAY", "COUNT");

    private SystemConfigPolicy() {}

    public static void requireValidUpdate(String unit, int value) {
        if (unit == null || !INTEGER_UNITS.contains(unit)) {
            throw new SystemConfigRuleException("Tham số này không hỗ trợ cập nhật số nguyên.");
        }
        if (value <= 0) {
            throw new SystemConfigRuleException("Giá trị tham số phải là số nguyên dương.");
        }
    }
}
