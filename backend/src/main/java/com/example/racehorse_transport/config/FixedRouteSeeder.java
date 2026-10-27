package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.LocationRouteId;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.LocationRouteRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Adds a small, idempotent starter set of fixed routes after network locations are available. */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class FixedRouteSeeder implements CommandLineRunner {

    private record RouteSeed(String seedKey, String legacyName, String name, String description, List<String> locationCodes) {}

    private static final List<RouteSeed> ROUTES = List.of(
            new RouteSeed("starter-vn-central", "Mẫu nội địa: TP.HCM – Đà Nẵng",
                    "Tuyến nội địa: TP.HCM – Đà Nẵng",
                    "Tuyến tham khảo qua các điểm dừng ven biển; cần kiểm tra bản đồ trước khi vận hành.",
                    List.of("hcm", "REST-LONG-THANH", "REST-PHAN-THIET", "REST-PHAN-RANG",
                            "REST-NHA-TRANG", "REST-TUY-HOA", "REST-QUY-NHON", "REST-QUANG-NGAI", "dn")),
            new RouteSeed("starter-vn-cambodia-mocbai", "Mẫu quốc tế: TP.HCM – Phnom Penh qua Mộc Bài",
                    "Tuyến quốc tế: TP.HCM – Phnom Penh qua Mộc Bài",
                    "Tuyến tham khảo qua cửa khẩu Mộc Bài; cần kiểm tra bản đồ và điều kiện thông quan trước khi vận hành.",
                    List.of("hcm", "REST-TRANG-BANG", "GATE-MOC-BAI", "REST-BAVET",
                            "REST-SVAY-RIENG", "REST-NEAK-LOEUNG", "pnh")),
            new RouteSeed("starter-vn-laos-laobao", "Mẫu quốc tế: TP.HCM – Viêng Chăn qua Lao Bảo",
                    "Tuyến quốc tế: TP.HCM – Viêng Chăn qua Lao Bảo",
                    "Tuyến tham khảo qua cửa khẩu Lao Bảo; cần kiểm tra bản đồ và điều kiện thông quan trước khi vận hành.",
                    List.of("hcm", "REST-QUANG-NGAI", "REST-DA-NANG", "REST-HUE", "REST-DONG-HA",
                            "GATE-LAO-BAO", "REST-SAVANNAKHET", "REST-THAKHEK", "REST-PAKSAN", "vte"))
    );

    private final RouteRepository routeRepository;
    private final LocationRepository locationRepository;
    private final LocationRouteRepository locationRouteRepository;

    @Override
    @Transactional
    public void run(String... args) {
        ensureGate("GATE-MOC-BAI", "Cửa khẩu quốc tế Mộc Bài - Bavet", "Bến Cầu", "Tây Ninh", "Việt Nam", 11.07, 106.2, "KH");
        ensureGate("GATE-LAO-BAO", "Cửa khẩu quốc tế Lao Bảo - Densavanh", "Hướng Hóa", "Quảng Trị", "Việt Nam", 16.62, 106.6, "LA");
        for (RouteSeed seed : ROUTES) {
            Route existing = routeRepository.findBySeedKey(seed.seedKey())
                    .or(() -> routeRepository.findByRouteNameIgnoreCase(seed.legacyName()))
                    .orElse(null);
            if (existing != null) {
                if (seed.legacyName().equals(existing.getRouteName())) {
                    existing.setRouteName(seed.name());
                    routeRepository.save(existing);
                    log.info("Renamed starter route '{}' to '{}'", seed.legacyName(), seed.name());
                }
                continue; // preserve Admin edits and make repeated startups safe
            }
            if (routeRepository.findByRouteNameIgnoreCase(seed.name()).isPresent()) {
                continue; // preserve Admin edits and make repeated startups safe
            }

            List<Location> locations = seed.locationCodes().stream()
                    .map(code -> locationRepository.findByLocationCodeIgnoreCase(code).orElse(null))
                    .toList();
            if (locations.stream().anyMatch(location -> location == null)) {
                log.warn("Skipped fixed route '{}' because a referenced location has not been seeded", seed.name());
                continue;
            }
            Route route = new Route();
            route.setId(routeRepository.findMaxId() + 1);
            route.setSeedKey(seed.seedKey());
            route.setRouteName(seed.name());
            route.setDescription(seed.description());
            route.setStatus("ACTIVE");
            route = routeRepository.save(route);

            List<LocationRoute> stops = new java.util.ArrayList<>();
            for (int i = 0; i < locations.size(); i++) {
                Location location = locations.get(i);
                LocationRouteId id = new LocationRouteId();
                id.setRouteID(route.getId());
                id.setLocationID(location.getId());
                LocationRoute stop = new LocationRoute();
                stop.setId(id);
                stop.setRouteID(route);
                stop.setLocationID(location);
                stop.setStopOrder(i + 1);
                stop.setLatitude(location.getLatitude());
                stop.setLongitude(location.getLongitude());
                stops.add(stop);
            }
            locationRouteRepository.saveAll(stops);
            log.info("Seeded fixed route '{}' with {} points", seed.name(), stops.size());
        }
    }

    private void ensureGate(String code, String name, String city, String province, String country,
                            double latitude, double longitude, String destinationCountry) {
        if (locationRepository.findByLocationCodeIgnoreCase(code).isPresent()) return;
        Location gate = new Location();
        gate.setId(locationRepository.findMaxId() + 1);
        gate.setLocationCode(code);
        gate.setLocationType("BorderGate");
        gate.setLocationName(name);
        gate.setAddress(name);
        gate.setCity(city);
        gate.setProvince(province);
        gate.setCountry(country);
        gate.setLatitude(latitude);
        gate.setLongitude(longitude);
        gate.setStatus("ACTIVE");
        gate.setOpeningMinutes(450);
        gate.setClosingMinutes(990);
        gate.setDestinationCountry(destinationCountry);
        locationRepository.save(gate);
    }
}
