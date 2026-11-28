package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.exception.SystemConfigRuleException;
import java.util.Set;
import java.util.regex.Pattern;

public final class SystemConfigPolicy {

    private static final Set<String> INTEGER_UNITS = Set.of("MINUTE", "HOUR", "HOURS", "DAY", "COUNT", "PERCENT");

    private static final String TEXT_UNIT = "TEXT";
    public static final String HOTLINE = "HOTLINE";
    public static final String OFFICE_ADDRESS = "OFFICE_ADDRESS";
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9](?:[0-9 .()-]{4,18})[0-9]");

    private SystemConfigPolicy() {}

    public static void requireValidText(String key, String unit, String value) {
        if (!TEXT_UNIT.equals(unit)) {
            throw new SystemConfigRuleException("Tham số này không hỗ trợ cập nhật bằng chữ.");
        }
        if (HOTLINE.equals(key) && !PHONE.matcher(value).matches()) {
            throw new SystemConfigRuleException("Hotline phải là số điện thoại hợp lệ.");
        }
    }

    public static void requireValidUpdate(String unit, int value, Integer minValue, Integer maxValue) {
        if (unit == null || !INTEGER_UNITS.contains(unit)) {
            throw new SystemConfigRuleException("Tham số này không hỗ trợ cập nhật số nguyên.");
        }
        if (minValue != null && maxValue != null) {
            if (value < minValue || value > maxValue) {
                throw new SystemConfigRuleException("Giá trị tham số phải nằm trong khoảng từ %d đến %d."
                        .formatted(minValue, maxValue));
            }
        } else if (value <= 0) {
            throw new SystemConfigRuleException("Giá trị tham số phải là số nguyên dương.");
        }
    }
}
