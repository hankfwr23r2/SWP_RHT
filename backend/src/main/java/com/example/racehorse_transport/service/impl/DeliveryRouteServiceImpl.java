package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.ValhallaClient;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.NoTruckRouteException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DeliveryRoutePolicy;
import com.example.racehorse_transport.policy.DeliveryRoutePolicy.Plan;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Rules;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.DeliveryRouteService;
import com.example.racehorse_transport.service.RouteCacheService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.racehorse_transport.entity.VehicleClass;
import com.example.racehorse_transport.entity.enums.ServiceLayer;
import com.example.racehorse_transport.policy.PricingPolicy.RoadLeg;
import com.example.racehorse_transport.policy.PricingPolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.repository.VehicleClassRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class DeliveryRouteServiceImpl implements DeliveryRouteService {
    private final LocationRepository locations;
    private final VehicleRepository vehicles;
    private final SystemConfigService configs;
    private final ValhallaClient valhalla;
    private final VehicleClassRepository classes;
    private final RouteCacheService cache;

    @Override
    @Transactional(readOnly = true)
    public DeliveryPricing pricing(Long hubId, List<Destination> destinations) {
        int seats = destinations.stream().mapToInt(Destination::seats).sum();
        Plan plan = plan(hubId, destinations.stream().map(Destination::point).toList(),
                destinations.stream().map(Destination::horseCount).toList(), seats);
        var vehicleClasses = classes.findAll();
        var loop = road(vehicleClasses, seats, plan.deliveryKm());
        List<RoadLeg> separate = new ArrayList<>();
        if (!plan.individualKm().contains(null)) {
            for (int i = 0; i < destinations.size(); i++) {
                separate.add(road(vehicleClasses, destinations.get(i).seats(), plan.individualKm().get(i)));
            }
        }
        long seconds = plan.drives().subList(0, plan.order().size()).stream().mapToLong(Long::longValue).sum();
        return new DeliveryPricing(loop, List.copyOf(separate), seconds, plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Instant> arrivalTimes(Plan plan, List<Destination> destinations, Instant readyAt) {
        var rules = rules();
        var window = new TripSchedulePolicy.Window(
                configs.getInt("DELIVERY_WINDOW_START_HOUR"), configs.getInt("DELIVERY_WINDOW_END_HOUR"));
        List<Integer> handled = new ArrayList<>();
        int totalHorses = destinations.stream().mapToInt(Destination::horseCount).sum();
        handled.add(totalHorses);
        plan.order().forEach(index -> handled.add(destinations.get(index - 1).horseCount()));
        handled.add(0);
        var start = TripSchedulePolicy.deliveryDeparture(
                readyAt.plus(TripSchedulePolicy.dwell(totalHorses, rules)),
                window, ZoneId.of("Asia/Ho_Chi_Minh"), handled, plan.drives(), rules);
        var schedule = TripSchedulePolicy.schedule(start, handled, plan.drives(), rules);
        return schedule.subList(1, schedule.size() - 1).stream()
                .map(TripSchedulePolicy.StopTime::arrival).toList();
    }

    private static RoadLeg road(
            List<VehicleClass> classes, int seats, double km) {
        Long classId = PricingPolicy.smallestVehicleClass(classes, seats).getId();
        return new RoadLeg(
                ServiceLayer.DELIVERY, classId,
                BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    @Transactional(readOnly = true)
    public Plan plan(Long hubId, List<GeoPointDto> destinations, List<Integer> horseCounts, int seats) {
        boolean hasVehicle = vehicles.findAllByOrderByLicensePlateAsc().stream()
                .anyMatch(vehicle -> vehicle.getStatus() == VehicleStatus.ACTIVE
                        && seats <= vehicle.getStandardStallCount() * StallSeatPolicy.seats("STANDARD")
                                + vehicle.getWideStallCount() * StallSeatPolicy.seats("WIDE"));
        if (!hasVehicle) throw new BookingRuleException("Không có một xe đủ khoang để giao cả đơn. Vui lòng đặt đơn riêng.");
        var hub = locations.findById(hubId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hub giao."));
        List<GeoPointDto> points = new ArrayList<>();
        points.add(new GeoPointDto(hub.getLatitude().doubleValue(), hub.getLongitude().doubleValue()));
        points.addAll(destinations);
        int weight = configs.getInt("TRUCK_WEIGHT_TONS");
        int speed = configs.getInt("TRUCK_TOP_SPEED_KMH");
        RoadRouteDto[][] roads = new RoadRouteDto[points.size()][points.size()];
        for (int from = 0; from < points.size(); from++) {
            for (int to = 0; to < points.size(); to++) {
                if (from == to) continue;
                try {
                    roads[from][to] = road(points.get(from), points.get(to), weight, speed);
                } catch (NoTruckRouteException ignored) {
                    // Another ordering may still connect both destinations.
                }
            }
        }
        return DeliveryRoutePolicy.shortest(roads, rules(), horseCounts)
                .orElseThrow(() -> new BookingRuleException("Không có vòng giao phù hợp đường xe tải và giới hạn giờ vận chuyển. Vui lòng đặt đơn riêng."));
    }

    private RoadRouteDto road(GeoPointDto from, GeoPointDto to, int weight, int speed) {
        String key = "truck|" + weight + '|' + speed + '|' + from.lat() + ',' + from.lng() + '|' + to.lat() + ',' + to.lng();
        return cache.find(key, RoadRouteDto.class).orElseGet(() -> {
            RoadRouteDto route = valhalla.truckRoute(List.of(from, to), weight, speed, null);
            cache.put(key, route);
            return route;
        });
    }

    private Rules rules() {
        return new Rules(configs.getInt("MAX_CONTINUOUS_HOURS"), configs.getInt("MIN_REST_MINUTES"),
                configs.getInt("MAX_DRIVE_HOURS_PER_DAY"), configs.getInt("STOP_BASE_MINUTES"), configs.getInt("STOP_MINUTES_PER_HORSE"));
    }
}
