package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.exception.HorseRuleException;

// Quy tắc hồ sơ ngựa. Mã chip bắt buộc, chuẩn ISO, không đổi sau khi lưu (nghiệp vụ cũ trên main).
public final class HorsePolicy {

    private HorsePolicy() {
    }

    // Trả về mã chip đã chuẩn hóa
    public static String requireMicrochip(String raw) {
        String microchip = MicrochipPolicy.normalize(raw);
        if (!MicrochipPolicy.isIso(microchip)) {
            throw new HorseRuleException("Mã microchip phải gồm đúng 15 chữ số (chuẩn ISO 11784/11785).");
        }
        return microchip;
    }

    public static void requireBirthYearNotAfter(Short birthYear, int currentYear) {
        if (birthYear != null && birthYear > currentYear) {
            throw new HorseRuleException("Nhập năm sinh hợp lệ.");
        }
    }
}
