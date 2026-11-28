package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.policy.HubResolutionPolicy.Routing;
import java.util.List;
import org.junit.jupiter.api.Test;

class HubResolutionPolicyTest {

    private static final Long HUB_HCM = 1L;
    private static final Long HUB_DN = 2L;

    @Test
    void interprovinceWithinSameHubRunsDirect() {
        // Đồng Nai -> Bình Dương: cùng HUB-HCM
        assertThat(HubResolutionPolicy.routingOf(BookingType.INTERPROVINCE, HUB_HCM, HUB_HCM)).isEqualTo(Routing.DIRECT);
    }

    @Test
    void interprovinceAcrossHubsUsesLinehaul() {
        // Đồng Nai -> Huế: HUB-HCM -> HUB-DN
        assertThat(HubResolutionPolicy.routingOf(BookingType.INTERPROVINCE, HUB_HCM, HUB_DN)).isEqualTo(Routing.LINEHAUL);
    }

    @Test
    void localAlwaysRunsDirect() {
        assertThat(HubResolutionPolicy.routingOf(BookingType.LOCAL, HUB_HCM, HUB_HCM)).isEqualTo(Routing.DIRECT);
    }

    @Test
    void internationalAlwaysUsesLinehaul() {
        assertThat(HubResolutionPolicy.routingOf(BookingType.EXPORT, HUB_HCM, 3L)).isEqualTo(Routing.LINEHAUL);
        assertThat(HubResolutionPolicy.routingOf(BookingType.IMPORT, 3L, HUB_HCM)).isEqualTo(Routing.LINEHAUL);
    }

    @Test
    void internationalBookingsRequireServingHubsAtBothEnds() {
        for (BookingType type : List.of(BookingType.EXPORT, BookingType.IMPORT)) {
            assertThatThrownBy(() -> HubResolutionPolicy.routingOf(type, null, HUB_HCM))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chưa có hub phục vụ");
            assertThatThrownBy(() -> HubResolutionPolicy.routingOf(type, HUB_HCM, null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chưa có hub phục vụ");
        }
    }

    @Test
    void reportsMissingHubsIncludingForeignEnds() {
        for (BookingType type : BookingType.values()) {
            assertThat(HubResolutionPolicy.missingPickupHub(type, null)).as("%s pickup", type).isTrue();
            assertThat(HubResolutionPolicy.missingPickupHub(type, HUB_HCM)).isFalse();
            assertThat(HubResolutionPolicy.missingDeliveryHub(type, HUB_HCM)).isFalse();
            assertThat(HubResolutionPolicy.missingDeliveryHub(type, null)).as("%s delivery", type)
                    .isEqualTo(type != BookingType.LOCAL);
        }
    }

    @Test
    void bookingTypeMustMatchPickupAndDeliveryPlaces() {
        HubResolutionPolicy.requireConsistentBookingType(BookingType.LOCAL, "VN", "VN", "VN-79", "VN-79");
        HubResolutionPolicy.requireConsistentBookingType(BookingType.INTERPROVINCE, "VN", "VN", "VN-75", "VN-46");
        HubResolutionPolicy.requireConsistentBookingType(BookingType.EXPORT, "VN", "KH", "VN-79", "KH-12");
        HubResolutionPolicy.requireConsistentBookingType(BookingType.IMPORT, "LA", "VN", "LA-VT", "VN-44");

        assertThatThrownBy(() -> HubResolutionPolicy.requireConsistentBookingType(BookingType.LOCAL, "VN", "VN", "VN-75", "VN-79"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HubResolutionPolicy.requireConsistentBookingType(BookingType.INTERPROVINCE, "VN", "KH", "VN-79", "KH-12"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HubResolutionPolicy.requireConsistentBookingType(BookingType.EXPORT, "KH", "VN", "KH-12", "VN-79"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsProvinceWithoutServingHub() {
        assertThatThrownBy(() -> HubResolutionPolicy.routingOf(BookingType.INTERPROVINCE, HUB_HCM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chưa có hub phục vụ");
        assertThatThrownBy(() -> HubResolutionPolicy.routingOf(BookingType.EXPORT, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
