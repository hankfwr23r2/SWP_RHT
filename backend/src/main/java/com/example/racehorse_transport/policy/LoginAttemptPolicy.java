package com.example.racehorse_transport.policy;

import java.time.Duration;
import java.time.Instant;

// Khóa tạm tài khoản khi nhập sai mật khẩu liên tiếp. Ngưỡng và thời gian khóa lấy từ system_config
// (LOGIN_MAX_FAILED_ATTEMPTS, LOGIN_LOCK_MINUTES). Khác trạng thái LOCKED do quản trị khóa tay.
public final class LoginAttemptPolicy {

    // Kết quả ghi vào app_user.failed_login_count và app_user.locked_until
    public record Attempts(int failedCount, Instant lockedUntil) {
    }

    private LoginAttemptPolicy() {
    }

    public static boolean isLocked(Instant lockedUntil, Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    // Số phút còn khóa, làm tròn lên, tối thiểu 1
    public static long minutesLeft(Instant lockedUntil, Instant now) {
        long seconds = Math.max(0, Duration.between(now, lockedUntil).getSeconds());
        return Math.max(1, (seconds + 59) / 60);
    }

    // Nhập sai: tăng bộ đếm; đủ ngưỡng thì khóa tạm và đếm lại từ 0
    public static Attempts afterFailure(int failedCount, int maxAttempts, int lockMinutes, Instant now) {
        if (maxAttempts < 1 || lockMinutes < 1) {
            throw new IllegalArgumentException("Ngưỡng nhập sai và thời gian khóa phải dương.");
        }
        int next = failedCount + 1;
        if (next >= maxAttempts) {
            return new Attempts(0, now.plus(Duration.ofMinutes(lockMinutes)));
        }
        return new Attempts(next, null);
    }

    // Nhập đúng: xóa bộ đếm và hạn khóa cũ
    public static Attempts afterSuccess() {
        return new Attempts(0, null);
    }
}
