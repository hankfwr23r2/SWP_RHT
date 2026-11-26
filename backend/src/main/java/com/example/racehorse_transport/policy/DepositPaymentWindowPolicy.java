package com.example.racehorse_transport.policy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// Hạn đặt cọc qua VNPAY, chính xác tới giây. Khoảng chờ chỉ để nhận kết quả của lần trả trước hạn, không gia hạn báo giá.
// Quy tắc lấy từ DepositPaymentWindow cũ trên main.
public final class DepositPaymentWindowPolicy {

    private DepositPaymentWindowPolicy() {
    }

    public static Instant expiresAt(Instant quotationExpiresAt) {
        return quotationExpiresAt.truncatedTo(ChronoUnit.SECONDS);
    }

    // Trước mốc này báo giá đang có phiên thanh toán chờ kết quả thì chưa được cho hết hạn
    public static Instant confirmationUntil(Instant quotationExpiresAt, int graceMinutes) {
        return expiresAt(quotationExpiresAt).plus(Math.max(0, graceMinutes), ChronoUnit.MINUTES);
    }

    // paidAt: giờ trả tiền do cổng báo. receivedAt: giờ hệ thống nhận kết quả.
    public static boolean canConfirm(Instant quotationExpiresAt, Instant sessionCreatedAt, Instant paidAt, Instant receivedAt,
                                     int graceMinutes) {
        return paidAt != null
                && !paidAt.isBefore(sessionCreatedAt.truncatedTo(ChronoUnit.SECONDS))
                && paidAt.isBefore(expiresAt(quotationExpiresAt))
                && !paidAt.isAfter(receivedAt)
                && receivedAt.isBefore(confirmationUntil(quotationExpiresAt, graceMinutes));
    }
}
