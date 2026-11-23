package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Hạn VNPAY có độ chính xác giây; khoảng chờ chỉ dành cho kết quả thanh toán trước hạn. */
public final class DepositPaymentWindow {
    private DepositPaymentWindow() { }

    public static Instant expiresAt(Quotation quote) {
        return quote.getExpiresAt().truncatedTo(ChronoUnit.SECONDS);
    }

    public static Instant confirmationUntil(Quotation quote, int graceMinutes) {
        return expiresAt(quote).plus(Math.max(0, graceMinutes), ChronoUnit.MINUTES);
    }

    public static boolean belongsToQuote(Payment payment, Quotation quote) {
        Instant createdAt = payment.getPaymentDate(); // PENDING: thời điểm tạo phiên, chưa phải thời điểm trả tiền.
        return createdAt != null && createdAt.isBefore(expiresAt(quote))
                && (quote.getSentAt() == null || !createdAt.isBefore(quote.getSentAt()));
    }

    public static boolean hasPendingDeposit(List<Payment> payments, Quotation quote) {
        return payments.stream().anyMatch(p -> "DEPOSIT".equalsIgnoreCase(p.getPaymentType())
                && "VNPAY".equalsIgnoreCase(p.getPaymentMethod())
                && "PENDING".equalsIgnoreCase(p.getStatus()) && belongsToQuote(p, quote));
    }

    public static boolean canConfirm(Quotation quote, Payment payment, Instant paidAt,
                                     Instant receivedAt, int graceMinutes) {
        return paidAt != null && belongsToQuote(payment, quote)
                && !paidAt.isBefore(payment.getPaymentDate().truncatedTo(ChronoUnit.SECONDS))
                && paidAt.isBefore(expiresAt(quote)) && !paidAt.isAfter(receivedAt)
                && receivedAt.isBefore(confirmationUntil(quote, graceMinutes));
    }
}
