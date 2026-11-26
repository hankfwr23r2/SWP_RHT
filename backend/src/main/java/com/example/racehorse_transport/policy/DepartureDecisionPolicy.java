package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

// Quyết định chạy chuyến trục (NGHIEP_VU_THEO_59_BANG.md mục 3.1). Hàm thuần.
public final class DepartureDecisionPolicy {

    // Đã cọc và còn trên chuyến
    public static final Set<BookingStatus> COMMITTED = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    // Chưa gửi báo giá: tới mốc quyết định thì nhả chỗ. AWAITING_DEPOSIT giữ chỗ tới khi báo giá hết hạn
    public static final Set<BookingStatus> RELEASABLE = Set.of(BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW,
            BookingStatus.NEEDS_RESUBMISSION, BookingStatus.QUOTING);
    // Đang giữ chỗ nhưng chưa cọc
    public static final Set<BookingStatus> UNCOMMITTED = Set.of(BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW,
            BookingStatus.NEEDS_RESUBMISSION, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT);
    // Chuyến chưa chạy
    public static final Set<DepartureStatus> NOT_STARTED = Set.of(DepartureStatus.OPEN, DepartureStatus.CLOSED);

    private DepartureDecisionPolicy() {
    }

    public static Instant decisionAt(Instant plannedDepartureAt, int decisionHours) {
        return plannedDepartureAt.minus(Duration.ofHours(decisionHours));
    }

    public static boolean belowMinimum(int committedSeats, int stallCapacity, int minFillPercent) {
        return (long) committedSeats * 100 < (long) stallCapacity * minFillPercent;
    }

    // Lý do chưa xác nhận chạy được; rỗng nghĩa là xác nhận được
    public static Optional<String> reasonNotConfirmable(DepartureStatus status, Instant plannedDepartureAt,
                                                        int decisionHours, boolean confirmed, Instant now) {
        if (!NOT_STARTED.contains(status)) {
            return Optional.of("Chuyến đã chạy hoặc đã hủy, không xác nhận chạy được.");
        }
        if (confirmed) {
            return Optional.of("Chuyến đã được xác nhận chạy.");
        }
        if (now.isBefore(decisionAt(plannedDepartureAt, decisionHours))) {
            return Optional.of("Chưa tới mốc quyết định, chỉ xác nhận chạy trong " + decisionHours + " giờ trước giờ xuất phát.");
        }
        if (!now.isBefore(plannedDepartureAt)) {
            return Optional.of("Chuyến đã qua giờ xuất phát.");
        }
        return Optional.empty();
    }

    // Đã nhận ngựa thì không hủy chuyến, xử lý bằng sự cố (người dùng chốt 24/11/2026)
    public static boolean blocksCancellation(BookingStatus status) {
        return status == BookingStatus.IN_OPERATION;
    }
}
