package com.example.racehorse_transport.service.routing;

import com.example.racehorse_transport.dto.routing.RoutePlace;
import com.example.racehorse_transport.dto.routing.RoutePreviewRequest;
import com.example.racehorse_transport.dto.routing.RoutePreviewResponse;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.service.FleetService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Journey;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Stop;
import com.example.racehorse_transport.service.routing.JourneyPlanner.StopKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// Xem trước lộ trình xe tải cho điều phối: tuyến Valhalla + phí cầu đường Vietmap (phần Việt Nam)
// + lịch trình JourneyPlanner + tên chỗ nghỉ dọc đường theo trạm nghỉ gần nhất
@Service
@RequiredArgsConstructor
public class RoutePreviewService {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_PATH_POINTS = 500;

    private final ValhallaClient valhalla;
    private final VietmapTollClient vietmap;
    private final RouteCache cache;
    private final LocationRepository locations;
    private final FleetService fleetService;
    private final SystemConfigService systemConfig;

    public RoutePreviewResponse preview(RoutePreviewRequest request) {
        List<Location> middle = stopsOf(request.stopLocationIds());
        Location gate = middle.stream().filter(RoutePreviewService::isGate).findFirst().orElse(null);
        Vehicle vehicle = fleetService.findVehicleByIdOrCode(request.vehicleId());
        int capacityKg = capacityKg(vehicle.getCapacity());
        int topSpeed = (int) number("TRUCK_TOP_SPEED_KMH");
        Instant departAt = Instant.ofEpochMilli(request.departAt());

        List<GeoPoint> points = new ArrayList<>();
        List<Stop> stops = new ArrayList<>();
        points.add(geo(request.pickup()));
        stops.add(new Stop(request.pickup().name(), StopKind.PICKUP, null, null));
        for (Location location : middle) {
            points.add(new GeoPoint(location.getLatitude(), location.getLongitude()));
            stops.add(isGate(location)
                    ? new Stop(location.getLocationName(), StopKind.GATE, location.getOpeningMinutes(), location.getClosingMinutes())
                    : new Stop(location.getLocationName(), StopKind.VIA, null, null));
        }
        points.add(geo(request.dropoff()));
        stops.add(new Stop(request.dropoff().name(), StopKind.DROPOFF, null, null));

        RoadRoute road = truckRoute(points, capacityKg, topSpeed, departAt);
        Journey journey = JourneyPlanner.plan(departAt, stops,
                road.legs().stream().map(leg -> new JourneyPlanner.Leg(leg.km(), leg.driveSeconds())).toList(),
                rules(), ZONE);

        List<String> warnings = new ArrayList<>();
        RoutePreviewResponse.Toll toll = toll(request, gate, capacityKg, departAt, warnings);

        List<String> names = stops.stream().map(Stop::name).toList();
        List<RoutePreviewResponse.Leg> legs = IntStream.range(0, road.legs().size())
                .mapToObj(i -> new RoutePreviewResponse.Leg(names.get(i), names.get(i + 1),
                        round1(road.legs().get(i).km()), hours(road.legs().get(i).driveSeconds())))
                .toList();
        double[] kmBeforeLeg = new double[road.legs().size()];
        for (int i = 1; i < kmBeforeLeg.length; i++) {
            kmBeforeLeg[i] = kmBeforeLeg[i - 1] + road.legs().get(i - 1).km();
        }
        List<RestPlaceNamer.Place> restStops = restStops();
        List<RoutePreviewResponse.Event> schedule = journey.events().stream()
                .map(event -> new RoutePreviewResponse.Event(event.type().name(),
                        placeOf(event, names, road, kmBeforeLeg, restStops),
                        event.startAt().toEpochMilli(), event.endAt().toEpochMilli()))
                .toList();

        return new RoutePreviewResponse("valhalla", round1(road.km()), hours(road.driveSeconds()), legs,
                path(road), toll, schedule, journey.arrivalAt().toEpochMilli(),
                journey.borderArrivalAt() == null ? null : journey.borderArrivalAt().toEpochMilli(),
                journey.crewDays(), warnings);
    }

    private RoadRoute truckRoute(List<GeoPoint> points, int capacityKg, int topSpeed, Instant departAt) {
        // Không gồm giờ xuất phát trong khóa: cùng tuyến cùng xe thì lộ trình và báo giá dùng cùng số liệu
        String key = "valhalla-truck|" + points.stream().map(GeoPoint::key).collect(Collectors.joining(";"))
                + "|" + capacityKg + "|" + topSpeed;
        return cache.find(key, RoadRoute.class).orElseGet(() -> {
            RoadRoute fresh = valhalla.truckRoute(points, capacityKg / 1000.0, topSpeed, LocalDateTime.ofInstant(departAt, ZONE));
            cache.put(key, fresh);
            return fresh;
        });
    }

    // Phí cầu đường chỉ có ở phần đường trong Việt Nam (Vietmap chỉ phủ Việt Nam)
    private RoutePreviewResponse.Toll toll(RoutePreviewRequest request, Location gate, int capacityKg, Instant departAt,
                                           List<String> warnings) {
        GeoPoint pickup = geo(request.pickup());
        GeoPoint dropoff = geo(request.dropoff());
        boolean pickupInVietnam = isVietnam(request.pickup().country());
        boolean dropoffInVietnam = isVietnam(request.dropoff().country());
        GeoPoint from = null;
        GeoPoint to = null;
        if (gate == null) {
            if (pickupInVietnam && dropoffInVietnam) {
                from = pickup;
                to = dropoff;
            }
        } else {
            GeoPoint gatePoint = new GeoPoint(gate.getLatitude(), gate.getLongitude());
            if (pickupInVietnam) {
                from = pickup;
                to = gatePoint;
            } else if (dropoffInVietnam) {
                from = gatePoint;
                to = dropoff;
            }
        }
        if (from == null) {
            return new RoutePreviewResponse.Toll(null, null);
        }
        String key = "vietmap-toll|" + from.key() + "|" + to.key() + "|" + capacityKg;
        Long cached = cache.find(key, Long.class).orElse(null);
        if (cached != null) {
            return new RoutePreviewResponse.Toll(cached, "vietmap");
        }
        OptionalLong fresh = vietmap.truckToll(from, to, capacityKg, departAt);
        if (fresh.isEmpty()) {
            warnings.add("Chưa lấy được phí cầu đường (Vietmap).");
            return new RoutePreviewResponse.Toll(null, "vietmap");
        }
        cache.put(key, fresh.getAsLong());
        return new RoutePreviewResponse.Toll(fresh.getAsLong(), "vietmap");
    }

    private List<Location> stopsOf(List<Integer> ids) {
        if (ids == null) {
            return List.of();
        }
        List<Location> result = new ArrayList<>();
        for (Integer id : ids) {
            Location location = locations.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa điểm #" + id + "."));
            if (location.getLatitude() == null || location.getLongitude() == null) {
                throw new IllegalArgumentException(location.getLocationName() + " chưa có tọa độ.");
            }
            result.add(location);
        }
        if (result.stream().filter(RoutePreviewService::isGate).count() > 1) {
            throw new IllegalArgumentException("Tuyến chỉ đi qua một cửa khẩu.");
        }
        return result;
    }

    private String placeOf(JourneyPlanner.Event event, List<String> names, RoadRoute road, double[] kmBeforeLeg,
                           List<RestPlaceNamer.Place> restStops) {
        if (event.place() != null) {
            return event.place();
        }
        int leg = event.legIndex();
        if (event.type() == JourneyPlanner.EventType.DRIVE) {
            return names.get(leg) + " → " + names.get(leg + 1);
        }
        return RestPlaceNamer.nameFor(road.legs().get(leg).shape(), event.kmIntoLeg(),
                kmBeforeLeg[leg] + event.kmIntoLeg(), restStops);
    }

    private List<RestPlaceNamer.Place> restStops() {
        return locations.findAll().stream()
                .filter(l -> "RestStop".equals(l.getLocationType()) && "ACTIVE".equals(l.getStatus())
                        && l.getLatitude() != null && l.getLongitude() != null)
                .map(l -> new RestPlaceNamer.Place(l.getLocationName(), new GeoPoint(l.getLatitude(), l.getLongitude())))
                .toList();
    }

    private JourneyPlanner.Rules rules() {
        return new JourneyPlanner.Rules(number("MAX_CONTINUOUS_HOURS"), (int) number("MIN_REST_MINUTES"),
                number("MAX_DRIVE_HOURS_PER_DAY"), number("OVERNIGHT_REST_HOURS"), number("BORDER_PROCESSING_HOURS"));
    }

    private double number(String key) {
        return Double.parseDouble(systemConfig.getConfig(key).getConfigValue());
    }

    // Tải trọng tạm theo số ngăn, chờ bảng hạng xe (spec mục 3.5)
    static int capacityKg(int stalls) {
        if (stalls <= 2) return 3500;
        if (stalls <= 4) return 8000;
        return 15000;
    }

    private static List<double[]> path(RoadRoute road) {
        List<GeoPoint> all = road.legs().stream().flatMap(leg -> leg.shape().stream()).toList();
        if (all.size() <= MAX_PATH_POINTS) {
            return all.stream().map(p -> new double[] {p.lat(), p.lng()}).toList();
        }
        double step = (all.size() - 1) / (double) (MAX_PATH_POINTS - 1);
        return IntStream.range(0, MAX_PATH_POINTS)
                .mapToObj(i -> all.get((int) Math.round(i * step)))
                .map(p -> new double[] {p.lat(), p.lng()})
                .toList();
    }

    private static boolean isGate(Location location) {
        return "BorderGate".equals(location.getLocationType());
    }

    private static boolean isVietnam(String country) {
        return "VN".equalsIgnoreCase(country);
    }

    private static GeoPoint geo(RoutePlace place) {
        return new GeoPoint(place.lat(), place.lng());
    }

    private static double round1(double km) {
        return Math.round(km * 10) / 10.0;
    }

    private static double hours(long seconds) {
        return Math.round(seconds / 36.0) / 100.0;
    }
}
