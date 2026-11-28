package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.BookingType;
import java.util.List;

// Mô hình bến xe (quyết định 26/11/2026): tuyến trục luôn nối hai hub, kể cả hub nước ngoài.
// Cửa khẩu là điểm dừng BORDER giữa tuyến. Hàm thuần.
public final class DepartureRoutePolicy {

    private DepartureRoutePolicy() {}

    // Đơn đi trục khớp tuyến khi hub đón là điểm đầu và hub giao là điểm cuối
    public static boolean matches(Route route, BookingType type, Long pickupHubId, Long deliveryHubId) {
        return type != BookingType.LOCAL
                && route.getOrigin().getId().equals(pickupHubId)
                && route.getDestination().getId().equals(deliveryHubId);
    }

    // Cửa khẩu đơn quốc tế đi qua: điểm dừng BORDER đầu tiên của tuyến. Đơn trong nước không có cửa khẩu
    public static Long gateId(BookingType type, List<Long> borderLocationIds) {
        if (type != BookingType.EXPORT && type != BookingType.IMPORT) {
            return null;
        }
        return borderLocationIds == null || borderLocationIds.isEmpty() ? null : borderLocationIds.get(0);
    }
}
