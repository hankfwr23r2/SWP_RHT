package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.BookingType;
import java.util.Objects;

// Chọn cách chạy của đơn theo hub, không theo loại đơn (DB_SCHEMA_59_BANG.md quy tắc #19).
// Hub đón, hub giao tra từ hub_service_area theo tỉnh của địa chỉ đón, giao. Hàm thuần.
public final class HubResolutionPolicy {

    public enum Routing {
        // Một chặng DIRECT từ địa chỉ đón tới địa chỉ giao, không có departure
        DIRECT,
        // Đón về hub, đi chuyến trục, rồi giao; bắt buộc có departure
        LINEHAUL
    }

    private HubResolutionPolicy() {
    }

    // pickupHubId, deliveryHubId: hub phục vụ tỉnh của điểm đón, điểm giao. Phía nước ngoài của đơn quốc tế để null.
    public static Routing routingOf(BookingType bookingType, Long pickupHubId, Long deliveryHubId) {
        Objects.requireNonNull(bookingType, "bookingType");
        return switch (bookingType) {
            // Cùng tỉnh nên luôn chạy thẳng
            case LOCAL -> {
                requireServed(pickupHubId);
                yield Routing.DIRECT;
            }
            // Đơn quốc tế luôn đi trục tới hoặc từ cửa khẩu
            case EXPORT, IMPORT -> {
                requireServed(bookingType == BookingType.EXPORT ? pickupHubId : deliveryHubId);
                yield Routing.LINEHAUL;
            }
            case INTERPROVINCE -> {
                requireServed(pickupHubId);
                requireServed(deliveryHubId);
                yield pickupHubId.equals(deliveryHubId) ? Routing.DIRECT : Routing.LINEHAUL;
            }
        };
    }

    private static void requireServed(Long hubId) {
        if (hubId == null) {
            throw new IllegalArgumentException("Tỉnh của địa chỉ này chưa có hub phục vụ.");
        }
    }
}
