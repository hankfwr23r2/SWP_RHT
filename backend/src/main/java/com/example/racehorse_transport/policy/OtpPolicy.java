package com.example.racehorse_transport.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.random.RandomGenerator;

// Quy tắc mã OTP dùng chung cho xác thực email và quên mật khẩu. Hàm thuần: thời gian và bộ sinh số ngẫu nhiên do bên gọi truyền vào.
// Thời hạn mã và số lần nhập sai tối đa lấy từ system_config (OTP_EXPIRY_MINUTES, OTP_MAX_ATTEMPTS).
public final class OtpPolicy {

    // Khoảng cách tối thiểu giữa hai lần gửi mã cho cùng email và mục đích: chống spam hộp thư, không phải tham số nghiệp vụ
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private OtpPolicy() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    // Mã 6 chữ số
    public static String generateCode(RandomGenerator random) {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    // Chưa từng gửi, hoặc lần gửi trước đã qua khoảng chờ
    public static boolean canSendAgain(Instant lastSentAt, Instant now) {
        return lastSentAt == null || !lastSentAt.isAfter(now.minus(RESEND_COOLDOWN));
    }

    public static Instant expiryOf(Instant sentAt, int expiryMinutes) {
        return sentAt.plus(Duration.ofMinutes(expiryMinutes));
    }

    // Mã còn dùng được: chưa hết hạn và chưa nhập sai quá số lần cho phép
    public static boolean isUsable(Instant expiresAt, int attempts, int maxAttempts, Instant now) {
        return expiresAt.isAfter(now) && attempts < maxAttempts;
    }

    // Sau một lần nhập sai, mã bị hủy nếu đã chạm số lần tối đa
    public static boolean isExhausted(int attemptsAfterFailure, int maxAttempts) {
        return attemptsAfterFailure >= maxAttempts;
    }
}
