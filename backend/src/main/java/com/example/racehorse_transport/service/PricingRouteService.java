package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.entity.enums.ServiceLayer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PricingRouteService {
    BigDecimal distance(RoadSegment segment, LocalDateTime time, int weightTons, int topSpeedKmh);

    RoadEstimate route(RoadSegment segment, LocalDateTime time, int weightTons, int topSpeedKmh);

    // Lộ trình xe tải qua nhiều điểm, từng đoạn kèm đường vẽ
    List<RoadRouteDto.Leg> legs(List<GeoPointDto> points, LocalDateTime time, int weightTons, int topSpeedKmh);

    record RoadEstimate(BigDecimal km, long driveSeconds) {}

    record RoadSegment(ServiceLayer layer, GeoPointDto from, GeoPointDto to, int maxDriveHoursPerDay) {
        public RoadSegment(ServiceLayer layer, GeoPointDto from, GeoPointDto to) {
            this(layer, from, to, 0);
        }
    }
}
