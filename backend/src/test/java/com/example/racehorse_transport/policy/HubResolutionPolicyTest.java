package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.policy.HubResolutionPolicy.Routing;
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
        // Phía nước ngoài không có hub
        assertThat(HubResolutionPolicy.routingOf(BookingType.EXPORT, HUB_HCM, null)).isEqualTo(Routing.LINEHAUL);
        assertThat(HubResolutionPolicy.routingOf(BookingType.IMPORT, null, HUB_HCM)).isEqualTo(Routing.LINEHAUL);
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
