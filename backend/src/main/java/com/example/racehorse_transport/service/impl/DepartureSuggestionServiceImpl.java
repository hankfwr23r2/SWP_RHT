package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.departure.DepartureResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionQueryDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionResponseDto;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.policy.DepartureCapacityPolicy;
import com.example.racehorse_transport.policy.HubResolutionPolicy;
import com.example.racehorse_transport.policy.HubResolutionPolicy.Routing;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.HubServiceAreaRepository;
import com.example.racehorse_transport.repository.ProvinceRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.service.DepartureSuggestionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartureSuggestionServiceImpl implements DepartureSuggestionService {

    private static final int MAX_SUGGESTIONS = 20;

    private final ProvinceRepository provinces;
    private final HubServiceAreaRepository serviceAreas;
    private final RouteRepository routes;
    private final DepartureRepository departures;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DepartureSuggestionResponseDto suggest(DepartureSuggestionQueryDto query) {
        Province pickup = province(query.getPickupProvinceCode());
        Province delivery = province(query.getDeliveryProvinceCode());
        HubResolutionPolicy.requireConsistentBookingType(query.getBookingType(), pickup.getCountry().getCode(),
                delivery.getCountry().getCode(), pickup.getCode(), delivery.getCode());

        Location pickupHub = hubOf(pickup.getCode());
        Location deliveryHub = hubOf(delivery.getCode());
        Routing routing = HubResolutionPolicy.routingOf(query.getBookingType(),
                pickupHub == null ? null : pickupHub.getId(), deliveryHub == null ? null : deliveryHub.getId());

        DepartureSuggestionResponseDto.DepartureSuggestionResponseDtoBuilder response = DepartureSuggestionResponseDto.builder()
                .routing(routing)
                .pickupHubCode(pickupHub == null ? null : pickupHub.getCode())
                .deliveryHubCode(deliveryHub == null ? null : deliveryHub.getCode());
        if (routing == Routing.DIRECT) {
            return response.departures(List.of()).build();
        }

        // Tuyến phù hợp và khoảng cách từ cửa khẩu tới điểm ở nước ngoài (0 với tuyến nội địa) để xếp thứ tự
        Map<Long, Double> routeDistance = routes.findByStatusWithEndpoints(ActiveStatus.ACTIVE).stream()
                .filter(routeMatcher(query.getBookingType(), pickupHub, deliveryHub))
                .collect(Collectors.toMap(Route::getId, route -> foreignLegKm(query, route)));
        if (routeDistance.isEmpty()) {
            return response.departures(List.of()).build();
        }

        List<DepartureResponseDto> found = departures.findBookable(routeDistance.keySet(), DepartureStatus.OPEN, clock.instant(),
                        query.getFromDate(), query.getStalls()).stream()
                .sorted(Comparator.<Departure>comparingDouble(d -> routeDistance.get(d.getRoute().getId()))
                        .thenComparing(Departure::getPlannedDepartAt))
                .limit(MAX_SUGGESTIONS)
                .map(d -> DepartureResponseDto.from(d, DepartureCapacityPolicy.available(d.getCapacityStalls(), d.getBookedStalls())))
                .toList();
        return response.departures(found).build();
    }

    // Tuyến nội địa nối đúng hai hub; xuất khẩu đi từ hub đón tới cửa khẩu; nhập khẩu đi từ cửa khẩu về hub giao
    private static Predicate<Route> routeMatcher(BookingType type, Location pickupHub, Location deliveryHub) {
        return route -> switch (type) {
            case INTERPROVINCE -> route.getOrigin().getId().equals(pickupHub.getId())
                    && route.getDestination().getId().equals(deliveryHub.getId());
            case EXPORT -> route.getOrigin().getId().equals(pickupHub.getId())
                    && route.getDestination().getType() == LocationType.BORDER_GATE;
            case IMPORT -> route.getOrigin().getType() == LocationType.BORDER_GATE
                    && route.getDestination().getId().equals(deliveryHub.getId());
            case LOCAL -> false;
        };
    }

    // Quãng đường chim bay giữa cửa khẩu và điểm ở nước ngoài, chỉ dùng để xếp hạng; thiếu tọa độ thì coi như bằng nhau
    private static double foreignLegKm(DepartureSuggestionQueryDto query, Route route) {
        if (query.getBookingType() == BookingType.EXPORT) {
            return distance(route.getDestination(), query.getDeliveryLatitude(), query.getDeliveryLongitude());
        }
        if (query.getBookingType() == BookingType.IMPORT) {
            return distance(route.getOrigin(), query.getPickupLatitude(), query.getPickupLongitude());
        }
        return 0;
    }

    private static double distance(Location gate, BigDecimal latitude, BigDecimal longitude) {
        if (gate.getLatitude() == null || latitude == null || longitude == null) {
            return 0;
        }
        return GeoPointDto.distanceKm(new GeoPointDto(gate.getLatitude().doubleValue(), gate.getLongitude().doubleValue()),
                new GeoPointDto(latitude.doubleValue(), longitude.doubleValue()));
    }

    private Province province(String code) {
        return provinces.findById(code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Tỉnh không hợp lệ: " + code));
    }

    // Hub phục vụ tỉnh; null nếu tỉnh chưa thuộc hub nào hoặc ở nước ngoài
    private Location hubOf(String provinceCode) {
        return serviceAreas.findById_ProvinceCode(provinceCode)
                .map(area -> area.getHub())
                .filter(hub -> hub.getStatus() == ActiveStatus.ACTIVE)
                .orElse(null);
    }
}
