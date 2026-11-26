package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.RouteStopType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Kiểm một tuyến trục chính và các điểm dừng của nó trước khi lưu. Hàm thuần: trả về danh sách lỗi, rỗng là hợp lệ.
public final class RouteStopsPolicy {

    // Mô hình bến xe (26/11/2026): tuyến luôn nối hai hub; cửa khẩu chỉ là điểm dừng giữa tuyến
    static final Set<LocationType> ORIGIN_TYPES = Set.of(LocationType.HUB);
    static final Set<LocationType> DESTINATION_TYPES = Set.of(LocationType.HUB);
    // Loại điểm dừng nào phải đặt ở loại địa điểm nào. REST và VITAL_CHECK đặt ở đâu cũng được
    static final Map<RouteStopType, LocationType> REQUIRED_LOCATION = Map.of(
            RouteStopType.HUB, LocationType.HUB,
            RouteStopType.BORDER, LocationType.BORDER_GATE);

    public record Stop(int sequenceNumber, Long locationId, LocationType locationType, RouteStopType stopType, int arrivalOffsetMinutes, int dwellMinutes) {
    }

    public record Limits(int maxContinuousHours, int minRestMinutes, int maxDriveHoursPerDay, int overnightRestHours) {
    }

    private RouteStopsPolicy() {
    }

    public static List<String> violations(Long originId, LocationType originType, Long destinationId, LocationType destinationType,
                                          List<Stop> stops, Limits limits) {
        List<String> errors = new ArrayList<>();
        if (!ORIGIN_TYPES.contains(originType)) {
            errors.add("Điểm đầu tuyến phải là hub.");
        }
        if (!DESTINATION_TYPES.contains(destinationType)) {
            errors.add("Điểm cuối tuyến phải là hub.");
        }
        if (Objects.equals(originId, destinationId)) {
            errors.add("Điểm đầu và điểm cuối tuyến phải khác nhau.");
        }
        if (stops == null || stops.size() < 2) {
            errors.add("Tuyến phải có ít nhất 2 điểm dừng.");
            return errors;
        }

        List<Stop> ordered = stops.stream().sorted(Comparator.comparingInt(Stop::sequenceNumber)).toList();
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).sequenceNumber() != i + 1) {
                errors.add("Thứ tự điểm dừng phải liên tục từ 1 đến " + ordered.size() + ".");
                break;
            }
        }
        Stop first = ordered.get(0);
        Stop last = ordered.get(ordered.size() - 1);
        if (!Objects.equals(first.locationId(), originId)) {
            errors.add("Điểm dừng đầu tiên phải là điểm đầu tuyến.");
        }
        if (!Objects.equals(last.locationId(), destinationId)) {
            errors.add("Điểm dừng cuối cùng phải là điểm cuối tuyến.");
        }
        if (first.arrivalOffsetMinutes() != 0) {
            errors.add("Điểm dừng đầu tiên phải có thời điểm 0 phút.");
        }
        long drivenSinceOvernightRest = 0;
        long maxContinuousMinutes = Duration.ofHours(limits.maxContinuousHours()).toMinutes();
        long maxDailyMinutes = Duration.ofHours(limits.maxDriveHoursPerDay()).toMinutes();
        long overnightMinutes = Duration.ofHours(limits.overnightRestHours()).toMinutes();
        for (int i = 0; i < ordered.size(); i++) {
            Stop stop = ordered.get(i);
            if (stop.arrivalOffsetMinutes() < 0 || stop.dwellMinutes() < 0) {
                errors.add("Điểm dừng " + stop.sequenceNumber() + ": số phút không được âm.");
            }
            if (stop.stopType() == RouteStopType.QUARANTINE) {
                errors.add("Điểm dừng " + stop.sequenceNumber() + ": không được chọn loại cách ly.");
            }
            if ((stop.stopType() == RouteStopType.REST || stop.stopType() == RouteStopType.VITAL_CHECK)
                    && stop.dwellMinutes() < limits.minRestMinutes()) {
                errors.add("Điểm dừng " + stop.sequenceNumber() + " phải nghỉ ít nhất " + limits.minRestMinutes() + " phút.");
            }
            LocationType required = REQUIRED_LOCATION.get(stop.stopType());
            if (required != null && required != stop.locationType()) {
                errors.add("Điểm dừng " + stop.sequenceNumber() + ": loại " + stop.stopType() + " phải đặt tại địa điểm loại " + required + ".");
            }
            if (i > 0) {
                Stop previous = ordered.get(i - 1);
                long driveMinutes = (long) stop.arrivalOffsetMinutes() - previous.arrivalOffsetMinutes() - previous.dwellMinutes();
                if (driveMinutes < 0) {
                    errors.add("Điểm dừng " + stop.sequenceNumber() + " phải tới sau khi xe rời điểm dừng " + previous.sequenceNumber() + ".");
                    continue;
                }
                if (driveMinutes > maxContinuousMinutes) {
                    errors.add("Đoạn tới điểm dừng " + stop.sequenceNumber() + " vượt giới hạn chạy liên tục.");
                }
                if (previous.dwellMinutes() >= overnightMinutes) {
                    drivenSinceOvernightRest = 0;
                }
                drivenSinceOvernightRest += driveMinutes;
                if (drivenSinceOvernightRest > maxDailyMinutes) {
                    errors.add("Đoạn tới điểm dừng " + stop.sequenceNumber() + " vượt giới hạn giờ lái giữa hai lần nghỉ đêm.");
                }
            }
        }
        return errors;
    }
}
