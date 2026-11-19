package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

// Đơn nội địa không được đi qua Campuchia/Lào. Ranh giới lấy từ Natural Earth 1:50m nên chỉ tính khi điểm nằm sâu
// hơn ngưỡng (3 km) bên trong lãnh thổ, để đường chạy sát biên trong Việt Nam không bị báo nhầm.
class TerritoryLookupServiceImplTest {

    private final TerritoryLookupServiceImpl territory = new TerritoryLookupServiceImpl(new ObjectMapper());

    @Test
    void pointsDeepInsideCambodiaOrLaosAreForeign() {
        assertThat(territory.countryDeeperThan(new GeoPointDto(11.087, 105.80), 3)).isEqualTo("Campuchia"); // Svay Rieng
        assertThat(territory.countryDeeperThan(new GeoPointDto(11.5564, 104.9282), 3)).isEqualTo("Campuchia"); // Phnom Penh
        assertThat(territory.countryDeeperThan(new GeoPointDto(17.97, 102.63), 3)).isEqualTo("Lào"); // Viêng Chăn
    }

    @Test
    void pointsInVietnamOrRightAtTheBorderAreNotForeign() {
        assertThat(territory.countryDeeperThan(new GeoPointDto(10.7769, 106.7009), 3)).isNull(); // TP.HCM
        assertThat(territory.countryDeeperThan(new GeoPointDto(21.0278, 105.8342), 3)).isNull(); // Hà Nội
        assertThat(territory.countryDeeperThan(new GeoPointDto(11.07, 106.2), 3)).isNull(); // Trạm nghỉ Mộc Bài (sát biên)
        assertThat(territory.countryDeeperThan(new GeoPointDto(11.08, 106.15), 3)).isNull(); // Bavet, sát biên
    }

    @Test
    void depthThresholdMatters() {
        // Svay Rieng sâu ~11,8 km trong ranh giới
        assertThat(territory.countryDeeperThan(new GeoPointDto(11.087, 105.80), 20)).isNull();
    }

    @Test
    void identifiesVietnamAndThirdCountriesForInternationalLegs() {
        assertThat(territory.countryCodeDeeperThan(new GeoPointDto(10.7769, 106.7009), 5)).isEqualTo("VN");
        assertThat(territory.countryCodeDeeperThan(new GeoPointDto(11.5564, 104.9282), 5)).isEqualTo("KH");
        assertThat(territory.countryCodeDeeperThan(new GeoPointDto(16.55, 105.0), 5)).isEqualTo("LA");
        assertThat(territory.countryCodeDeeperThan(new GeoPointDto(13.7563, 100.5018), 5)).isEqualTo("TH");
        assertThat(territory.countryCodeDeeperThan(new GeoPointDto(22.817, 108.366), 5)).isEqualTo("CN");
        assertThat(territory.countryCodeDeeperThan(new GeoPointDto(16.866, 96.195), 5)).isEqualTo("MM");
        assertThat(territory.countryDeeperThan(new GeoPointDto(13.7563, 100.5018), 5)).isEqualTo("Thái Lan");
    }
}
