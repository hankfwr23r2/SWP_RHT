package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Departure;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public final class DepartureMergePolicy {

    private DepartureMergePolicy() {
    }

    public static boolean canMergeSource(Departure source) {
        return source.getDepartureSchedule() != null && DepartureCapacityPolicy.canCancel(source.getStatus());
    }

    public static Optional<String> reasonNotMergeable(Departure source, Departure target, LocalDate holdThrough, Instant now) {
        if (source.getId().equals(target.getId())) {
            return Optional.of("Chọn chuyến đích khác chuyến nguồn.");
        }
        if (source.getDepartureSchedule() == null || target.getDepartureSchedule() == null) {
            return Optional.of("Chỉ dồn giữa các chuyến theo lịch, không dồn chuyến đột xuất.");
        }
        if (!source.getRoute().getId().equals(target.getRoute().getId())) {
            return Optional.of("Chuyến đích phải cùng tuyến với chuyến nguồn.");
        }
        if (!DepartureDelayPolicy.notStarted(target.getStatus()) || !now.isBefore(DepartureDelayPolicy.effectiveTime(target))) {
            return Optional.of("Chuyến đích đã qua giờ xuất phát, đã chạy hoặc đã hủy.");
        }
        if (!withinPriceHold(source, target, holdThrough)) {
            return Optional.of("Chuyến đích nằm ngoài hạn giữ giá.");
        }
        if (DepartureCapacityPolicy.available(target.getStallCapacity(), target.getBookedStallCount()) < source.getBookedStallCount()) {
            return Optional.of("Chuyến đích không đủ chỗ cho các đơn đang giữ chỗ trên chuyến nguồn.");
        }
        return Optional.empty();
    }

    public static boolean withinPriceHold(Departure source, Departure target, LocalDate holdThrough) {
        LocalDate targetDate = target.getDepartureDate();
        return !targetDate.isBefore(source.getDepartureDate()) && !targetDate.isAfter(holdThrough);
    }
}
