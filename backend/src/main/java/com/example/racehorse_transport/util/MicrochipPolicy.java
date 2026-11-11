package com.example.racehorse_transport.util;

// Mã microchip ngựa theo chuẩn ISO 11784/11785: đúng 15 chữ số.
// Người nhập hay máy quét có thể chèn dấu cách/gạch nối, nên luôn chuẩn hóa trước khi so sánh hoặc lưu.
public final class MicrochipPolicy {

    private MicrochipPolicy() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase();
    }

    public static boolean isIso(String normalized) {
        return normalized.matches("\\d{15}");
    }
}
