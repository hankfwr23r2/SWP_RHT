package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.departure.DepartureResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionQueryDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionResponseDto.Unavailable;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.CountryRule;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.entity.enums.RuleDirection;
import com.example.racehorse_transport.policy.DepartureRoutePolicy;
import com.example.racehorse_transport.policy.DepartureCapacityPolicy;
import com.example.racehorse_transport.policy.HubResolutionPolicy;
import com.example.racehorse_transport.policy.HubResolutionPolicy.Routing;
import com.example.racehorse_transport.policy.RegulationPolicy;
import com.example.racehorse_transport.repository.CountryRuleRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.HubServiceAreaRepository;
import com.example.racehorse_transport.repository.ProvinceRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.service.DepartureSuggestionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;

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
    private final CountryRuleRepository countryRules;
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
        Long pickupHubId = pickupHub == null ? null : pickupHub.getId();
        Long deliveryHubId = deliveryHub == null ? null : deliveryHub.getId();
        boolean noPickupHub = HubResolutionPolicy.missingPickupHub(query.getBookingType(), pickupHubId);
        if (noPickupHub || HubResolutionPolicy.missingDeliveryHub(query.getBookingType(), deliveryHubId)) {
            return DepartureSuggestionResponseDto.builder().departures(List.of())
                    .unavailable(noPickupHub ? Unavailable.PICKUP_NOT_SERVED : Unavailable.DELIVERY_NOT_SERVED)
                    .servedProvinces(servedProvinces()).build();
        }
        Routing routing = HubResolutionPolicy.routingOf(query.getBookingType(), pickupHubId, deliveryHubId);

        DepartureSuggestionResponseDto.DepartureSuggestionResponseDtoBuilder response = DepartureSuggestionResponseDto.builder()
                .routing(routing)
                .pickupHubCode(pickupHub == null ? null : pickupHub.getCode())
                .pickupHubName(pickupHub == null ? null : pickupHub.getName())
                .deliveryHubCode(deliveryHub == null ? null : deliveryHub.getCode())
                .deliveryHubName(deliveryHub == null ? null : deliveryHub.getName());
        if (routing == Routing.DIRECT) {
            return response.departures(List.of()).build();
        }

        // Tuyến nối đúng hub đón với hub giao (mô hình bến xe)
        Set<Long> routeIds = routes.findByStatusWithEndpoints(ActiveStatus.ACTIVE).stream()
                .filter(route -> DepartureRoutePolicy.matches(route, query.getBookingType(), pickupHubId, deliveryHubId))
                .map(Route::getId).collect(Collectors.toSet());
        if (routeIds.isEmpty()) {
            return response.departures(List.of()).unavailable(Unavailable.NO_ROUTE).build();
        }

        List<Departure> found = departures.findBookable(routeIds, DepartureStatus.OPEN, clock.instant(),
                        query.getFromDate(), query.getStalls()).stream()
                .sorted(Comparator.comparing(Departure::getPlannedDepartureAt))
                .limit(MAX_SUGGESTIONS)
                .toList();
        List<CountryRule> rules = countryRules.findByCountry_CodeAndDirection(
                RegulationPolicy.partnerCountry(query.getBookingType(), pickup.getCountry().getCode(), delivery.getCountry().getCode()),
                RuleDirection.valueOf(query.getBookingType().name()));
        return response
                .departures(found.stream().map(d -> DepartureResponseDto.from(d,
                        DepartureCapacityPolicy.available(d.getStallCapacity(), d.getBookedStallCount()))).toList())
                .leadDays(found.isEmpty() ? null : RegulationPolicy.leadDays(rules, found.get(0).getDepartureDate()))
                .tooLateDepartureIds(found.stream().filter(d -> !RegulationPolicy.feasible(
                        RegulationPolicy.leadDays(rules, d.getDepartureDate()), clock.instant(), d.getBookingCutoffAt()))
                        .map(Departure::getId).toList())
                .build();
    }

    private List<String> servedProvinces() {
        return serviceAreas.findAllWithHubAndProvince().stream()
                .filter(area -> area.getHub().getStatus() == ActiveStatus.ACTIVE)
                .map(area -> area.getProvince().getName()).sorted().toList();
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
