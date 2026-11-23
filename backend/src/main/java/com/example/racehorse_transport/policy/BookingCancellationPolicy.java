package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.math.BigDecimal;
import java.util.Set;

// Quy tắc hủy đơn và tách tiền (người dùng chốt 23/11/2026, theo nghiệp vụ cũ trên main).
public final class BookingCancellationPolicy {

    private static final Set<BookingStatus> BEFORE_CONFIRMED = Set.of(BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW,
            BookingStatus.NEEDS_RESUBMISSION, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, BookingStatus.QUOTE_EXPIRED);

    private BookingCancellationPolicy() {
    }

    public enum Outcome {
        // Chưa trả tiền, hoặc công ty hủy chuyến: không mất phí
        FREE,
        // Đã cọc: mất phần cọc vận chuyển, phần thu hộ do quản lý quyết định hoàn
        FORFEIT_DEPOSIT,
        // Khách không tự hủy được, phải liên hệ quản lý
        NOT_ALLOWED
    }

    // refundable: hoàn chắc chắn. reviewable: phần thu hộ chờ quản lý quyết định. forfeited: khách mất.
    public record Breakdown(BigDecimal refundable, BigDecimal reviewable, BigDecimal forfeited) {
    }

    public static Outcome byCustomer(BookingStatus status, boolean depositPaid, boolean paidInFull) {
        if (status == BookingStatus.SCHEDULE_CHANGE_REQUIRED) {
            return Outcome.FREE;
        }
        if (BEFORE_CONFIRMED.contains(status)) {
            return depositPaid ? Outcome.NOT_ALLOWED : Outcome.FREE;
        }
        if (status == BookingStatus.CONFIRMED) {
            return paidInFull ? Outcome.NOT_ALLOWED : Outcome.FORFEIT_DEPOSIT;
        }
        return Outcome.NOT_ALLOWED;
    }

    // Công ty chỉ hủy được khi chưa nhận ngựa
    public static boolean companyCanCancel(BookingStatus status) {
        return BEFORE_CONFIRMED.contains(status) || status == BookingStatus.CONFIRMED
                || status == BookingStatus.SCHEDULE_CHANGE_REQUIRED;
    }

    // Công ty hủy, hoặc khách hủy không mất phí: hoàn toàn bộ số đã trả, gồm phần thu hộ
    public static Breakdown refundAll(BigDecimal paid) {
        return new Breakdown(paid, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    // Khách hủy sau khi cọc. Tiền cọc gồm toàn bộ phần thu hộ nên phần còn lại là cọc vận chuyển
    public static Breakdown forfeit(BigDecimal paid, BigDecimal passThroughAmount) {
        BigDecimal reviewable = passThroughAmount.min(paid);
        return new Breakdown(BigDecimal.ZERO, reviewable, paid.subtract(reviewable));
    }
}
