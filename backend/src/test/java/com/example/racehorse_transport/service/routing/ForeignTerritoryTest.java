package com.example.racehorse_transport.service.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Đơn nội địa không được đi qua Campuchia/Lào. Ranh giới lấy từ Natural Earth 1:50m nên chỉ tính khi điểm nằm sâu
// hơn ngưỡng (3 km) bên trong lãnh thổ, để đường chạy sát biên trong Việt Nam không bị báo nhầm.
class ForeignTerritoryTest {

    private final ForeignTerritory territory = new ForeignTerritory(new ObjectMapper());

    @Test
    void pointsDeepInsideCambodiaOrLaosAreForeign() {
        assertThat(territory.countryDeeperThan(new GeoPoint(11.087, 105.80), 3)).isEqualTo("Campuchia"); // Svay Rieng
        assertThat(territory.countryDeeperThan(new GeoPoint(11.5564, 104.9282), 3)).isEqualTo("Campuchia"); // Phnom Penh
        assertThat(territory.countryDeeperThan(new GeoPoint(17.97, 102.63), 3)).isEqualTo("Lào"); // Viêng Chăn
    }

    @Test
    void pointsInVietnamOrRightAtTheBorderAreNotForeign() {
        assertThat(territory.countryDeeperThan(new GeoPoint(10.7769, 106.7009), 3)).isNull(); // TP.HCM
        assertThat(territory.countryDeeperThan(new GeoPoint(21.0278, 105.8342), 3)).isNull(); // Hà Nội
        assertThat(territory.countryDeeperThan(new GeoPoint(11.07, 106.2), 3)).isNull(); // Trạm nghỉ Mộc Bài (sát biên)
        assertThat(territory.countryDeeperThan(new GeoPoint(11.08, 106.15), 3)).isNull(); // Bavet, sát biên
    }

    @Test
    void depthThresholdMatters() {
        // Svay Rieng sâu ~11,8 km trong ranh giới
        assertThat(territory.countryDeeperThan(new GeoPoint(11.087, 105.80), 20)).isNull();
    }
}
