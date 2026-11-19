package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.route.GeoPointDto;

public interface TerritoryLookupService {

    String countryDeeperThan(GeoPointDto point, double depthKm);

    // Mã quốc gia, kể cả VN; null khi sát biên hoặc ngoài vùng dữ liệu.
    String countryCodeDeeperThan(GeoPointDto point, double depthKm);
}
