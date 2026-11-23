package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.enums.ServiceLayer;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PricingRouteService {
    BigDecimal distance(RoadSegment segment, LocalDateTime time, int weightTons, int topSpeedKmh);

    record RoadSegment(ServiceLayer layer, GeoPointDto from, GeoPointDto to) {}
}
