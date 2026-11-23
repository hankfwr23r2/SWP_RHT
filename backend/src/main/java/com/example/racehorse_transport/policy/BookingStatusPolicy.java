package com.example.racehorse_transport.policy;

import static com.example.racehorse_transport.entity.enums.BookingStatus.AWAITING_DEPOSIT;
import static com.example.racehorse_transport.entity.enums.BookingStatus.CANCELLED;
import static com.example.racehorse_transport.entity.enums.BookingStatus.COMPLETED;
import static com.example.racehorse_transport.entity.enums.BookingStatus.CONFIRMED;
import static com.example.racehorse_transport.entity.enums.BookingStatus.DELIVERED;
import static com.example.racehorse_transport.entity.enums.BookingStatus.IN_OPERATION;
import static com.example.racehorse_transport.entity.enums.BookingStatus.NEEDS_RESUBMISSION;
import static com.example.racehorse_transport.entity.enums.BookingStatus.PENDING_INTAKE;
import static com.example.racehorse_transport.entity.enums.BookingStatus.QUOTE_EXPIRED;
import static com.example.racehorse_transport.entity.enums.BookingStatus.QUOTING;
import static com.example.racehorse_transport.entity.enums.BookingStatus.REJECTED;
import static com.example.racehorse_transport.entity.enums.BookingStatus.SCHEDULE_CHANGE_REQUIRED;
import static com.example.racehorse_transport.entity.enums.BookingStatus.UNDER_REVIEW;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// Bảng chuyển trạng thái hợp lệ của đơn (12 mốc thương mại, NGHIEP_VU_THEO_59_BANG.md mục 4 và 5). Hàm thuần.
// Tiến độ vận hành không nằm ở đây: suy ra từ horse_leg, horse_stay, handover, incident.
public final class BookingStatusPolicy {

    private static final Map<BookingStatus, Set<BookingStatus>> NEXT = new EnumMap<>(BookingStatus.class);

    static {
        NEXT.put(PENDING_INTAKE, EnumSet.of(UNDER_REVIEW, SCHEDULE_CHANGE_REQUIRED, REJECTED, CANCELLED));
        NEXT.put(UNDER_REVIEW, EnumSet.of(NEEDS_RESUBMISSION, QUOTING, SCHEDULE_CHANGE_REQUIRED, REJECTED, CANCELLED));
        NEXT.put(NEEDS_RESUBMISSION, EnumSet.of(UNDER_REVIEW, SCHEDULE_CHANGE_REQUIRED, CANCELLED));
        NEXT.put(QUOTING, EnumSet.of(AWAITING_DEPOSIT, SCHEDULE_CHANGE_REQUIRED, REJECTED, CANCELLED));
        // Khách không đồng ý báo giá thì lập phiên bản mới (quay lại QUOTING)
        NEXT.put(AWAITING_DEPOSIT, EnumSet.of(CONFIRMED, QUOTE_EXPIRED, QUOTING, SCHEDULE_CHANGE_REQUIRED, CANCELLED));
        NEXT.put(QUOTE_EXPIRED, EnumSet.of(QUOTING, CANCELLED));
        NEXT.put(CONFIRMED, EnumSet.of(IN_OPERATION, SCHEDULE_CHANGE_REQUIRED, CANCELLED));
        // Chuyến bị hủy hoặc đổi: khách chọn chuyến khác (giữ giá thì CONFIRMED, cần báo giá mới thì QUOTING) hoặc hủy đơn
        NEXT.put(SCHEDULE_CHANGE_REQUIRED, EnumSet.of(UNDER_REVIEW, QUOTING, CONFIRMED, CANCELLED));
        NEXT.put(IN_OPERATION, EnumSet.of(DELIVERED));
        NEXT.put(DELIVERED, EnumSet.of(COMPLETED));
        NEXT.put(COMPLETED, EnumSet.noneOf(BookingStatus.class));
        NEXT.put(CANCELLED, EnumSet.noneOf(BookingStatus.class));
        NEXT.put(REJECTED, EnumSet.noneOf(BookingStatus.class));
    }

    private BookingStatusPolicy() {
    }

    public static boolean canTransition(BookingStatus from, BookingStatus to) {
        return from != null && to != null && NEXT.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<BookingStatus> nextOf(BookingStatus from) {
        return Set.copyOf(NEXT.getOrDefault(from, Set.of()));
    }
}
