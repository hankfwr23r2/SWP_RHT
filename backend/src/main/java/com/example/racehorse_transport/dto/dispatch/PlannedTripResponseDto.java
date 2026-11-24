package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.enums.TripStopType;
import java.time.Instant;
import java.util.List;

// Kế hoạch giờ của một lượt xe trước khi lập. blocked: có cảnh báo không cho lập
public record PlannedTripResponseDto(
        Instant startAt, Instant endAt, long driveMinutes, double km, boolean blocked,
        List<Warning> warnings, List<PlannedStopDto> stops) {

    public enum Warning {
        // Con ngựa ở trên xe quá giới hạn giờ lái một chiều
        RIDE_TOO_LONG(true),
        // Ngựa tới hub sau hạn có mặt của chuyến trục
        LATE_FOR_HUB(true),
        // Lượt xe lái quá giới hạn giờ trong ngày
        DRIVER_DAY_LIMIT(true),
        // Đón hoặc giao ngoài khung giờ
        OUTSIDE_WINDOW(false);

        private final boolean blocking;

        Warning(boolean blocking) {
            this.blocking = blocking;
        }

        public boolean blocking() {
            return blocking;
        }
    }

    // driveMinutes, km, restMinutes, path: đoạn đường tới điểm này
    public record PlannedStopDto(
            Long locationId, Long bookingAddressId, TripStopType stopType, String name, String bookingCode,
            GeoPointDto point, int horses, Instant arrivalAt, Instant departureAt,
            long driveMinutes, double km, int restMinutes, List<GeoPointDto> path) {
    }
}
