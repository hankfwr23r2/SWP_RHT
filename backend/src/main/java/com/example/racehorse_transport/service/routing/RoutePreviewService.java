package com.example.racehorse_transport.service.routing;

import com.example.racehorse_transport.dto.routing.RoadRouteRequest;
import com.example.racehorse_transport.dto.routing.RoadRouteResponse;
import com.example.racehorse_transport.dto.routing.RoutePlace;
import com.example.racehorse_transport.dto.routing.RoutePreviewRequest;
import com.example.racehorse_transport.dto.routing.RoutePreviewResponse;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.exception.DomesticRouteAbroadException;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.service.FleetService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Journey;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Station;
import com.example.racehorse_transport.service.routing.JourneyPlanner.Stop;
import com.example.racehorse_transport.service.routing.JourneyPlanner.StopKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// Xem trước lộ trình xe tải cho điều phối: tuyến Valhalla. Phí cầu đường do tài xế ghi theo hóa đơn thực tế
// + lịch trình JourneyPlanner (nghỉ ở trạm nghỉ thật trên đường khi có) + tên chỗ nghỉ dọc đường theo trạm gần nhất.
// Đơn nội địa: chỉ dừng ở trạm trong nước, không qua cửa khẩu, tuyến không được đi sâu vào Campuchia/Lào.
@Service
@RequiredArgsConstructor
public class RoutePreviewService {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_PATH_POINTS = 500;
    // Trạm nghỉ coi là "trên đường" khi cách đường vẽ không quá 5 km
    private static final double STATION_MAX_OFF_ROUTE_KM = 5;
    // Ranh giới Natural Earth thô: chỉ tính là ra nước ngoài khi sâu hơn 5 km
    private static final double FOREIGN_DEPTH_KM = 5;
    // Tuyến nội địa lọt ra nước ngoài: thử ép đi qua tối đa 4 trạm nghỉ trong nước, không vòng quá 1,6 lần đường thẳng
    private static final int MAX_DETOUR_TRIES = 4;
    // Máy chủ Valhalla công khai nhận tối đa 10 điểm mỗi lần tính: điểm đón + điểm trả + 8 điểm giữa (trạm, cửa khẩu, điểm đi qua)
    static final int MAX_ROUTE_POINTS = 10;
    private static final double MAX_DETOUR_RATIO = 1.6;

    private final ValhallaClient valhalla;
    private final RouteCache cache;
    private final LocationRepository locations;
    private final FleetService fleetService;
    private final SystemConfigService systemConfig;
    private final ForeignTerritory foreignTerritory;

    public RoutePreviewResponse preview(RoutePreviewRequest request) {
        List<Location> middle = stopsOf(request.stopLocationIds());
        if (middle.size() > MAX_ROUTE_POINTS - 2) {
            throw new IllegalArgumentException("Chọn tối đa " + (MAX_ROUTE_POINTS - 2) + " trạm (tính cả cửa khẩu), đang có "
                    + middle.size() + ". Hãy bỏ bớt trạm.");
        }
        Location gate = middle.stream().filter(RoutePreviewService::isGate).findFirst().orElse(null);
        if (!request.pickup().country().equalsIgnoreCase(request.dropoff().country()) && gate == null) {
            throw new IllegalArgumentException("Tuyến đi nước ngoài cần chọn một cửa khẩu.");
        }
        boolean domestic = isVietnam(request.pickup().country()) && isVietnam(request.dropoff().country());
        if (domestic) {
            if (gate != null) {
                throw new IllegalArgumentException("Đơn nội địa không đi qua cửa khẩu.");
            }
            for (Location location : middle) {
                if (!"VN".equals(countryCode(location.getCountry()))) {
                    throw new IllegalArgumentException("Đơn nội địa chỉ dừng ở trạm trong nước: " + location.getLocationName() + ".");
                }
            }
        }
        Vehicle vehicle = fleetService.findVehicleByIdOrCode(request.vehicleId());
        int capacityKg = capacityKg(vehicle.getCapacity());
        int topSpeed = (int) number("TRUCK_TOP_SPEED_KMH");
        Instant departAt = Instant.ofEpochMilli(request.departAt());
        middle = orderAlongRoad(request, middle, gate, capacityKg, topSpeed, departAt);

        List<GeoPoint> points = new ArrayList<>();
        List<Stop> stops = new ArrayList<>();
        Map<String, GeoPoint> knownPlaces = new HashMap<>();
        points.add(geo(request.pickup()));
        stops.add(new Stop(request.pickup().name(), StopKind.PICKUP, null, null));
        knownPlaces.put(request.pickup().name(), geo(request.pickup()));
        for (Location location : middle) {
            GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
            points.add(point);
            knownPlaces.put(location.getLocationName(), point);
            stops.add(isGate(location)
                    ? new Stop(location.getLocationName(), StopKind.GATE, location.getOpeningMinutes(), location.getClosingMinutes())
                    : new Stop(location.getLocationName(), StopKind.VIA, null, null));
        }
        points.add(geo(request.dropoff()));
        stops.add(new Stop(request.dropoff().name(), StopKind.DROPOFF, null, null));
        knownPlaces.put(request.dropoff().name(), geo(request.dropoff()));
        List<String> names = stops.stream().map(Stop::name).toList();

        List<Location> restStopLocations = restStopLocations();
        RoadRoute road = domestic
                ? routeInsideVietnam(points, stops, middle, restStopLocations, capacityKg, topSpeed, departAt)
                : truckRoute(points.stream().map(ValhallaClient.Waypoint::stop).toList(), capacityKg, topSpeed, departAt);

        restStopLocations.forEach(l -> knownPlaces.putIfAbsent(l.getLocationName(), new GeoPoint(l.getLatitude(), l.getLongitude())));
        int gateIndex = gate == null ? -1 : middle.indexOf(gate) + 1;
        List<List<Station>> stations = IntStream.range(0, road.legs().size())
                .mapToObj(i -> stationsOnLeg(road.legs().get(i), restStopLocations,
                        gateIndex < 0 || i < gateIndex ? countryCode(request.pickup().country()) : countryCode(request.dropoff().country())))
                .toList();

        Journey journey = JourneyPlanner.plan(departAt, stops,
                road.legs().stream().map(leg -> new JourneyPlanner.Leg(leg.km(), leg.driveSeconds())).toList(),
                stations, rules(), ZONE);

        List<String> warnings = new ArrayList<>();

        List<RoutePreviewResponse.Leg> legs = IntStream.range(0, road.legs().size())
                .mapToObj(i -> new RoutePreviewResponse.Leg(names.get(i), names.get(i + 1),
                        round1(road.legs().get(i).km()), hours(road.legs().get(i).driveSeconds())))
                .toList();
        double[] kmBeforeLeg = new double[road.legs().size()];
        for (int i = 1; i < kmBeforeLeg.length; i++) {
            kmBeforeLeg[i] = kmBeforeLeg[i - 1] + road.legs().get(i - 1).km();
        }
        List<RestPlaceNamer.Place> namerPlaces = restStopLocations.stream()
                .map(l -> new RestPlaceNamer.Place(l.getLocationName(), new GeoPoint(l.getLatitude(), l.getLongitude())))
                .toList();
        List<RoutePreviewResponse.Event> schedule = journey.events().stream()
                .map(event -> toEvent(event, names, road, kmBeforeLeg, namerPlaces, knownPlaces))
                .toList();
        // Nghỉ dọc đường: nói rõ xe dừng ở đâu để điều phối quyết định có chọn thêm trạm hay không
        List<String> roadside = IntStream.range(0, journey.events().size())
                .filter(i -> journey.events().get(i).place() == null && journey.events().get(i).type() != JourneyPlanner.EventType.DRIVE)
                .mapToObj(i -> schedule.get(i).place())
                .map(place -> place.startsWith("Nghỉ dọc đường") ? place.replaceAll("^Nghỉ dọc đường \\((.*)\\)$", "$1") : "gần " + place)
                .toList();
        if (!roadside.isEmpty()) {
            warnings.add("Không có trạm nghỉ ở đoạn xe cần nghỉ, xe phải nghỉ dọc đường " + roadside.size() + " lần ("
                    + String.join("; ", roadside) + "). Có thể chọn thêm trạm nghỉ trên bản đồ.");
        }

        return new RoutePreviewResponse("valhalla", round1(road.km()), hours(road.driveSeconds()), legs,
                path(road), schedule, journey.arrivalAt().toEpochMilli(),
                journey.borderArrivalAt() == null ? null : journey.borderArrivalAt().toEpochMilli(),
                journey.crewDays(), warnings);
    }

    private RoutePreviewResponse.Event toEvent(JourneyPlanner.Event event, List<String> names, RoadRoute road,
                                               double[] kmBeforeLeg, List<RestPlaceNamer.Place> namerPlaces,
                                               Map<String, GeoPoint> knownPlaces) {
        int legIndex = event.legIndex();
        RoadRoute.Leg leg = road.legs().get(legIndex);
        long seconds = event.endAt().getEpochSecond() - event.startAt().getEpochSecond();
        if (event.type() == JourneyPlanner.EventType.DRIVE) {
            double km = leg.driveSeconds() == 0 ? 0 : leg.km() * seconds / leg.driveSeconds();
            return new RoutePreviewResponse.Event(event.type().name(), names.get(legIndex) + " → " + names.get(legIndex + 1),
                    event.startAt().toEpochMilli(), event.endAt().toEpochMilli(), round1(km), null, null);
        }
        String place = event.place() != null ? event.place()
                : RestPlaceNamer.nameFor(leg.shape(), event.kmIntoLeg(), kmBeforeLeg[legIndex] + event.kmIntoLeg(), namerPlaces);
        GeoPoint at = event.place() != null && knownPlaces.containsKey(event.place())
                ? knownPlaces.get(event.place())
                : RestPlaceNamer.pointAlong(leg.shape(), event.kmIntoLeg());
        return new RoutePreviewResponse.Event(event.type().name(), place,
                event.startAt().toEpochMilli(), event.endAt().toEpochMilli(), 0, at.lat(), at.lng());
    }

    // Trạm nghỉ cùng nước, cách đường vẽ của chặng không quá 5 km; vị trí đổi ra giây lái theo tỉ lệ km
    private static List<Station> stationsOnLeg(RoadRoute.Leg leg, List<Location> restStops, String country) {
        double shapeKm = RouteGeometry.lengthKm(leg.shape());
        if (leg.shape().isEmpty() || shapeKm <= 0 || leg.km() <= 0) {
            return List.of();
        }
        List<Station> result = new ArrayList<>();
        for (Location location : restStops) {
            if (!country.equals(countryCode(location.getCountry()))) {
                continue;
            }
            RouteGeometry.Projection projection = RouteGeometry.project(leg.shape(),
                    new GeoPoint(location.getLatitude(), location.getLongitude()));
            if (projection.distanceKm() > STATION_MAX_OFF_ROUTE_KM) {
                continue;
            }
            double km = leg.km() * projection.kmAlongShape() / shapeKm;
            long offset = Math.round(leg.driveSeconds() * km / leg.km());
            if (offset > 0 && offset < leg.driveSeconds()) {
                result.add(new Station(location.getLocationName(), offset, km));
            }
        }
        return result;
    }

    // Trạm điều phối chọn được xếp theo vị trí trên đường xe tải thật từ điểm đón tới điểm trả (qua cửa khẩu nếu có),
    // không theo thứ tự bấm hay đường chim bay: tuyến cong (Châu Đốc → Cao Lãnh → Bình Dương) vẫn ghé đúng thứ tự.
    private List<Location> orderAlongRoad(RoutePreviewRequest request, List<Location> middle, Location gate,
                                          int capacityKg, int topSpeed, Instant departAt) {
        if (middle.stream().filter(l -> !isGate(l)).count() < 2) {
            return middle;
        }
        List<ValhallaClient.Waypoint> base = new ArrayList<>();
        base.add(ValhallaClient.Waypoint.stop(geo(request.pickup())));
        if (gate != null) {
            base.add(ValhallaClient.Waypoint.stop(new GeoPoint(gate.getLatitude(), gate.getLongitude())));
        }
        base.add(ValhallaClient.Waypoint.stop(geo(request.dropoff())));
        List<GeoPoint> shape = truckRoute(base, capacityKg, topSpeed, departAt).legs().stream()
                .flatMap(leg -> leg.shape().stream()).toList();
        if (shape.isEmpty()) {
            return middle;
        }
        return middle.stream()
                .sorted(Comparator.comparingDouble((Location l) ->
                        RouteGeometry.project(shape, new GeoPoint(l.getLatitude(), l.getLongitude())).kmAlongShape()))
                .toList();
    }

    private record Abroad(int legIndex, String country) {}

    // Đơn nội địa: đường xe tải ngắn nhất có thể cắt qua Campuchia/Lào (vd. Châu Đốc → Bình Dương qua Svay Rieng).
    // Chặng nào lọt ra nước ngoài thì ép xe đi qua một trạm nghỉ trong nước (điểm đi qua, không dừng), thử trạm vòng ít
    // nhất trước; không trạm nào giúp được thì báo chặng và việc điều phối cần làm.
    private RoadRoute routeInsideVietnam(List<GeoPoint> points, List<Stop> stops, List<Location> middle,
                                         List<Location> restStops, int capacityKg, int topSpeed, Instant departAt) {
        Inside inside = insideVietnam(points, middle, restStops, capacityKg, topSpeed, departAt);
        if (inside.abroad() != null) {
            throw abroadException(inside.abroad(), stops);
        }
        return inside.road();
    }

    // abroad != null: không tìm được đường trong nước cho chặng đó, road là đường tốt nhất đã có
    private record Inside(RoadRoute road, Abroad abroad) {}

    private Inside insideVietnam(List<GeoPoint> points, List<Location> middle, List<Location> restStops,
                                 int capacityKg, int topSpeed, Instant departAt) {
        List<ValhallaClient.Waypoint> waypoints = new ArrayList<>(points.stream().map(ValhallaClient.Waypoint::stop).toList());
        RoadRoute road = truckRoute(waypoints, capacityKg, topSpeed, departAt);
        Abroad abroad = abroad(road);
        while (abroad != null) {
            if (waypoints.size() >= MAX_ROUTE_POINTS) {
                return new Inside(road, abroad);
            }
            int leg = abroad.legIndex();
            GeoPoint from = points.get(leg);
            GeoPoint to = points.get(leg + 1);
            List<GeoPoint> candidates = detourCandidates(from, to, middle, restStops);
            boolean fixed = false;
            for (GeoPoint candidate : candidates) {
                List<ValhallaClient.Waypoint> trial = new ArrayList<>(waypoints);
                trial.add(stopIndex(waypoints, leg + 1), new ValhallaClient.Waypoint(candidate, true));
                RoadRoute retry = truckRoute(trial, capacityKg, topSpeed, departAt);
                Abroad still = abroad(retry);
                if (still == null || still.legIndex() > leg) {
                    waypoints = trial;
                    road = retry;
                    abroad = still;
                    fixed = true;
                    break;
                }
            }
            if (!fixed) {
                return new Inside(road, abroad);
            }
        }
        return new Inside(road, null);
    }

    // Đường xe tải cho phương án sự cố. Điểm nào cũng ở Việt Nam thì giữ đường trong nước như lúc lập kế hoạch;
    // không giữ được thì vẫn trả đường (sự cố cần xử lý ngay) kèm cảnh báo.
    public RoadRouteResponse roadRoute(RoadRouteRequest request) {
        List<GeoPoint> points = withinRouteLimit(request.points().stream().map(p -> new GeoPoint(p.lat(), p.lng())).toList());
        int capacityKg = capacityKg(4);
        int topSpeed = (int) number("TRUCK_TOP_SPEED_KMH");
        Instant now = Instant.now();
        List<Location> restStops = restStopLocations();
        boolean domestic = points.stream().allMatch(p -> foreignTerritory.countryDeeperThan(p, FOREIGN_DEPTH_KM) == null);
        List<String> warnings = new ArrayList<>();
        RoadRoute main;
        if (domestic) {
            Inside inside = insideVietnam(points, List.of(), restStops, capacityKg, topSpeed, now);
            main = inside.road();
            if (inside.abroad() != null) {
                warnings.add("Đường xe tải đi qua lãnh thổ " + inside.abroad().country()
                        + ", chưa tìm được đường khác trong Việt Nam.");
            }
        } else {
            main = truckRoute(points.stream().map(ValhallaClient.Waypoint::stop).toList(), capacityKg, topSpeed, now);
        }
        List<RoadRouteResponse.Route> routes = new ArrayList<>();
        routes.add(roadRouteOf(main));
        if (request.alternatives() && points.size() == 2) {
            routes.addAll(detours(points.get(0), points.get(1), main, restStops, domestic, capacityKg, topSpeed, now));
        }
        return new RoadRouteResponse(routes, warnings);
    }

    // Đường tránh: ép đi qua trạm nghỉ nằm ngoài đường chính (cách hơn 5 km), vòng ít nhất trước, tối đa 2 đường.
    // Máy chủ Valhalla công khai không trả tuyến thay thế (alternates) nên dựng bằng điểm đi qua.
    private List<RoadRouteResponse.Route> detours(GeoPoint from, GeoPoint to, RoadRoute main, List<Location> restStops,
                                                  boolean domestic, int capacityKg, int topSpeed, Instant departAt) {
        List<GeoPoint> mainShape = main.legs().stream().flatMap(leg -> leg.shape().stream()).toList();
        double direct = Math.max(GeoPoint.distanceKm(from, to), 1);
        List<GeoPoint> candidates = restStops.stream()
                .filter(l -> !domestic || "VN".equals(countryCode(l.getCountry())))
                .map(l -> new GeoPoint(l.getLatitude(), l.getLongitude()))
                .filter(p -> mainShape.isEmpty() || RouteGeometry.project(mainShape, p).distanceKm() > STATION_MAX_OFF_ROUTE_KM)
                .filter(p -> GeoPoint.distanceKm(from, p) + GeoPoint.distanceKm(p, to) <= direct * MAX_DETOUR_RATIO)
                .sorted(Comparator.comparingDouble(p -> GeoPoint.distanceKm(from, p) + GeoPoint.distanceKm(p, to)))
                .limit(MAX_DETOUR_TRIES)
                .toList();
        List<RoadRouteResponse.Route> result = new ArrayList<>();
        for (GeoPoint candidate : candidates) {
            RoadRoute detour = truckRoute(List.of(ValhallaClient.Waypoint.stop(from), new ValhallaClient.Waypoint(candidate, true),
                    ValhallaClient.Waypoint.stop(to)), capacityKg, topSpeed, departAt);
            if (domestic && abroad(detour) != null) {
                continue;
            }
            result.add(roadRouteOf(detour));
            if (result.size() == 2) {
                break;
            }
        }
        return result;
    }

    // Đường cho phương án sự cố đi qua nhiều mốc còn lại: giữ điểm đầu, điểm cuối và rải đều các mốc giữa cho vừa giới hạn
    private static List<GeoPoint> withinRouteLimit(List<GeoPoint> points) {
        if (points.size() <= MAX_ROUTE_POINTS) {
            return points;
        }
        double step = (points.size() - 1) / (double) (MAX_ROUTE_POINTS - 1);
        return IntStream.range(0, MAX_ROUTE_POINTS).mapToObj(i -> points.get((int) Math.round(i * step))).toList();
    }

    private static RoadRouteResponse.Route roadRouteOf(RoadRoute road) {
        return new RoadRouteResponse.Route(round1(road.km()), hours(road.driveSeconds()), path(road));
    }

    // Trạm nghỉ trong nước chưa có trong tuyến, xếp theo quãng vòng thêm so với đi thẳng
    private static List<GeoPoint> detourCandidates(GeoPoint from, GeoPoint to, List<Location> middle, List<Location> restStops) {
        double direct = Math.max(GeoPoint.distanceKm(from, to), 1);
        return restStops.stream()
                .filter(l -> "VN".equals(countryCode(l.getCountry())))
                .filter(l -> middle.stream().noneMatch(m -> m.getId().equals(l.getId())))
                .map(l -> new GeoPoint(l.getLatitude(), l.getLongitude()))
                .filter(p -> GeoPoint.distanceKm(from, p) + GeoPoint.distanceKm(p, to) <= direct * MAX_DETOUR_RATIO)
                .sorted(Comparator.comparingDouble(p -> GeoPoint.distanceKm(from, p) + GeoPoint.distanceKm(p, to)))
                .limit(MAX_DETOUR_TRIES)
                .toList();
    }

    // Vị trí trong danh sách điểm của điểm dừng thứ stopNumber (bỏ qua các điểm đi qua đã chèn)
    private static int stopIndex(List<ValhallaClient.Waypoint> waypoints, int stopNumber) {
        int seen = -1;
        for (int i = 0; i < waypoints.size(); i++) {
            if (!waypoints.get(i).through() && ++seen == stopNumber) {
                return i;
            }
        }
        return waypoints.size();
    }

    private Abroad abroad(RoadRoute road) {
        for (int i = 0; i < road.legs().size(); i++) {
            for (GeoPoint point : road.legs().get(i).shape()) {
                String country = foreignTerritory.countryDeeperThan(point, FOREIGN_DEPTH_KM);
                if (country != null) {
                    return new Abroad(i, country);
                }
            }
        }
        return null;
    }

    private static DomesticRouteAbroadException abroadException(Abroad abroad, List<Stop> stops) {
        Stop from = stops.get(abroad.legIndex());
        Stop to = stops.get(abroad.legIndex() + 1);
        String via = to.kind() == StopKind.VIA ? to.name() : from.kind() == StopKind.VIA ? from.name() : null;
        return new DomesticRouteAbroadException("Tuyến nội địa đi qua lãnh thổ " + abroad.country() + " ở chặng "
                + from.name() + " → " + to.name() + "."
                + (via == null ? " Hãy chọn thêm một trạm nghỉ ở Việt Nam trên chặng này." : " Hãy bỏ hoặc đổi trạm " + via + "."));
    }

    private RoadRoute truckRoute(List<ValhallaClient.Waypoint> waypoints, int capacityKg, int topSpeed, Instant departAt) {
        // Không gồm giờ xuất phát trong khóa: cùng tuyến cùng xe thì lộ trình và báo giá dùng cùng số liệu
        String key = "valhalla-truck|" + waypoints.stream()
                .map(w -> (w.through() ? "~" : "") + w.point().key()).collect(Collectors.joining(";"))
                + "|" + capacityKg + "|" + topSpeed;
        return cache.find(key, RoadRoute.class).orElseGet(() -> {
            RoadRoute fresh = valhalla.truckRouteVia(waypoints, capacityKg / 1000.0, topSpeed, LocalDateTime.ofInstant(departAt, ZONE));
            cache.put(key, fresh);
            return fresh;
        });
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

    private List<Location> restStopLocations() {
        return locations.findAll().stream()
                .filter(l -> "RestStop".equals(l.getLocationType()) && "ACTIVE".equals(l.getStatus())
                        && l.getLatitude() != null && l.getLongitude() != null)
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

    // Bảng location lưu tên nước ("Việt Nam", "Campuchia", "Lào"); API dùng mã VN/KH/LA
    static String countryCode(String country) {
        String value = country == null ? "" : country.trim().toLowerCase(Locale.ROOT);
        if (value.equals("kh") || value.contains("campuchia") || value.contains("cambodia")) return "KH";
        if (value.equals("la") || value.contains("lào") || value.contains("laos")) return "LA";
        return "VN";
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
