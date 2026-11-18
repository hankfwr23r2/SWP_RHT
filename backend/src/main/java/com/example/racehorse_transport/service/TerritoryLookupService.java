package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.route.GeoPointDto;

public interface TerritoryLookupService {

    String countryDeeperThan(GeoPointDto point, double depthKm);
}
