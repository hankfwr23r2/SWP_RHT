package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

// Quy tắc đặt chỗ, nhả chỗ, đóng và hủy chuyến (DB_SCHEMA_59_BANG.md quy tắc #3, #25). Hàm thuần.
public final class DepartureCapacityPolicy {

    // Chỉ hủy được chuyến chưa chạy
    static final Set<DepartureStatus> CANCELLABLE = Set.of(DepartureStatus.OPEN, DepartureStatus.CLOSED);

    private DepartureCapacityPolicy() {
    }

    public static int available(int stallCapacity, int bookedStallCount) {
        return Math.max(0, stallCapacity - bookedStallCount);
    }

    // Lý do không đặt được chỗ; rỗng nghĩa là đặt được
    public static Optional<String> reasonNotReservable(DepartureStatus status, Instant bookingCutoffAt, Instant now,
                                                       int stallCapacity, int bookedStallCount, int stalls) {
        if (stalls <= 0) {
            return Optional.of("Số chỗ cần đặt phải lớn hơn 0.");
        }
        if (status != DepartureStatus.OPEN) {
            return Optional.of("Chuyến không còn nhận đặt chỗ.");
        }
        if (!now.isBefore(bookingCutoffAt)) {
            return Optional.of("Chuyến đã qua giờ chốt nhận đơn.");
        }
        if (bookedStallCount + stalls > stallCapacity) {
            return Optional.of("Chuyến chỉ còn " + available(stallCapacity, bookedStallCount) + " chỗ.");
        }
        return Optional.empty();
    }

    // Số chỗ đã đặt sau khi nhả. Nhả nhiều hơn số đã đặt là lỗi dữ liệu, không âm thầm làm tròn về 0.
    public static int bookedAfterRelease(int bookedStallCount, int stalls) {
        if (stalls <= 0 || stalls > bookedStallCount) {
            throw new IllegalStateException("Không nhả được " + stalls + " chỗ khi chuyến mới đặt " + bookedStallCount + " chỗ.");
        }
        return bookedStallCount - stalls;
    }

    // Chuyến đang nhận đơn mà đã qua giờ chốt thì đóng
    public static boolean shouldClose(DepartureStatus status, Instant bookingCutoffAt, Instant now) {
        return status == DepartureStatus.OPEN && !now.isBefore(bookingCutoffAt);
    }

    public static boolean canCancel(DepartureStatus status) {
        return CANCELLABLE.contains(status);
    }
}
