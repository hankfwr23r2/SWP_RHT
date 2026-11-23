package com.example.racehorse_transport.util;

import java.security.SecureRandom;

// Mã lượt xe hiển thị cho tổ xe và điều phối, cùng bảng chữ với mã đơn (bỏ 0/O, 1/I/L)
public final class VehicleTripCodes {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private VehicleTripCodes() {
    }

    public static String newCode() {
        StringBuilder code = new StringBuilder("VT-");
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
