package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.policy.DeliveryRoutePolicy.Plan;
import java.util.List;
import com.example.racehorse_transport.policy.PricingPolicy.RoadLeg;
import java.time.Instant;

public interface DeliveryRouteService {
    record Destination(GeoPointDto point, int horseCount, int seats) {}
    record DeliveryPricing(RoadLeg loop, List<RoadLeg> separate, long deliverySeconds, Plan plan) {}

    DeliveryPricing pricing(Long hubId, List<Destination> destinations);

    List<Instant> arrivalTimes(Plan plan, List<Destination> destinations, Instant readyAt);

    Plan plan(Long hubId, List<GeoPointDto> destinations, List<Integer> horseCounts, int seats);
}
