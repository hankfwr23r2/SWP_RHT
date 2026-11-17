package com.example.racehorse_transport.util;

import java.security.SecureRandom;

// Mã đơn hiển thị cho người dùng: ngẫu nhiên, không suy ra được từ BookingID tự tăng.
// Bỏ các ký tự dễ đọc nhầm (0/O, 1/I/L) để khách đọc mã qua điện thoại không bị sai.
public final class BookingCodes {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingCodes() {
    }

    public static String newCode() {
        StringBuilder code = new StringBuilder("BK-");
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
