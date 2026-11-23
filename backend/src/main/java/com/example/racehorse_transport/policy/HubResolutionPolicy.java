package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.BookingType;
import java.util.Objects;

// Chọn cách chạy của đơn theo hub, không theo loại đơn (DB_SCHEMA_59_BANG.md quy tắc #19).
// Hub đón, hub giao tra từ hub_service_area theo tỉnh của địa chỉ đón, giao. Hàm thuần.
public final class HubResolutionPolicy {

    static final String HOME_COUNTRY = "VN";

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

    // Loại đơn phải khớp nơi đón và nơi giao. Công ty đặt tại Việt Nam nên VN là phía trong nước.
    public static void requireConsistentBookingType(BookingType bookingType, String pickupCountry, String deliveryCountry,
                                                    String pickupProvince, String deliveryProvince) {
        boolean pickupHome = HOME_COUNTRY.equals(pickupCountry);
        boolean deliveryHome = HOME_COUNTRY.equals(deliveryCountry);
        boolean valid = switch (bookingType) {
            case LOCAL -> pickupHome && deliveryHome && Objects.equals(pickupProvince, deliveryProvince);
            case INTERPROVINCE -> pickupHome && deliveryHome && !Objects.equals(pickupProvince, deliveryProvince);
            case EXPORT -> pickupHome && !deliveryHome;
            case IMPORT -> !pickupHome && deliveryHome;
        };
        if (!valid) {
            throw new IllegalArgumentException("Loại đơn " + bookingType + " không khớp với nơi đón và nơi giao.");
        }
    }

    private static void requireServed(Long hubId) {
        if (hubId == null) {
            throw new IllegalArgumentException("Tỉnh của địa chỉ này chưa có hub phục vụ.");
        }
    }
}
