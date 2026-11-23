package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.dto.route.RoutePlaceDto;
import com.example.racehorse_transport.dto.route.RoutePreviewRequestDto;
import com.example.racehorse_transport.dto.route.RoutePreviewResponseDto;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.dto.admin.SystemConfigResponseDto;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.exception.DomesticRouteAbroadException;
import com.example.racehorse_transport.exception.NoTruckRouteException;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.service.FleetService;
import com.example.racehorse_transport.service.RoadRoutingService;
import com.example.racehorse_transport.service.RouteCacheService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Kiểm tra cả hình học đường đi và thuật toán thử lại, không phụ thuộc mạng hoặc dữ liệu trạm nghỉ có sẵn.
class RoutePreviewServiceImplTest {
    private static final GeoPointDto HCM = new GeoPointDto(10.7769, 106.7009);
    private static final GeoPointDto HANOI = new GeoPointDto(21.0278, 105.8342);
    private static final GeoPointDto PHNOM_PENH = new GeoPointDto(11.5564, 104.9282);
    private static final GeoPointDto MOC_BAI = new GeoPointDto(11.0796, 106.1572);
    private static final GeoPointDto LAO_BAO = new GeoPointDto(16.62, 106.59);
    private static final GeoPointDto SAVANNAKHET = new GeoPointDto(16.55, 104.75);
    private static final List<GeoPointDto> VIETNAM = List.of(HCM, new GeoPointDto(12.2388, 109.1967),
            new GeoPointDto(16.0544, 108.2022), new GeoPointDto(17.4689, 106.6223),
            new GeoPointDto(18.6796, 105.6813), HANOI);

    private final RoadRoutingService routing = mock(RoadRoutingService.class);
    private final RouteCacheService cache = mock(RouteCacheService.class);
    private final LocationRepository locations = mock(LocationRepository.class);
    private final FleetService fleet = mock(FleetService.class);
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final List<Location> catalog = new ArrayList<>();
    private final RoutePreviewServiceImpl service = new RoutePreviewServiceImpl(routing, cache, locations, fleet, config,
            new TerritoryLookupServiceImpl(new ObjectMapper()));

    @BeforeEach
    void setUp() {
        Vehicle truck = Vehicle.builder().vehicleType("Xe chuyên dụng 4 ngăn").build();
        when(fleet.findVehicleByIdOrCode("truck")).thenReturn(truck);
        when(locations.findAll()).thenAnswer(invocation -> catalog);
        when(locations.findById(anyInt())).thenAnswer(invocation -> catalog.stream()
                .filter(l -> l.getId().equals(invocation.getArgument(0))).findFirst());
        when(cache.find(anyString(), eq(RoadRouteDto.class))).thenReturn(Optional.empty());
        Map<String, String> rules = Map.of("TRUCK_TOP_SPEED_KMH", "80", "MAX_CONTINUOUS_HOURS", "4",
                "MIN_REST_MINUTES", "30", "MAX_DRIVE_HOURS_PER_DAY", "10", "OVERNIGHT_REST_HOURS", "10",
                "BORDER_PROCESSING_HOURS", "2");
        when(config.getConfig(anyString())).thenAnswer(invocation -> {
            SystemConfigResponseDto value = new SystemConfigResponseDto();
            value.setConfigValue(rules.get(invocation.<String>getArgument(0)));
            return value;
        });
    }

    @Test
    void longDomesticRouteUsesVietnamCorridorEvenWithNoRestStations() {
        RoadRouteDto foreign = road(List.of(HCM, PHNOM_PENH, HANOI));
        RoadRouteDto valid = road(VIETNAM);
        answerByThroughPoints(foreign, valid);

        RoutePreviewResponseDto preview = service.preview(request(HCM, "VN", HANOI, "VN", List.of()));

        assertThat(preview.km()).isEqualTo(valid.km());
        assertThat(preview.legs()).hasSize(1);
        assertThat(preview.path()).contains(new double[] {16.0544, 108.2022});
        ArgumentCaptor<List<RoadRoutingService.Waypoint>> capture = ArgumentCaptor.forClass(List.class);
        verify(routing, times(2)).truckRouteVia(capture.capture(), anyDouble(), anyInt(), any());
        List<RoadRoutingService.Waypoint> retry = capture.getAllValues().getLast();
        assertThat(retry.size()).isBetween(4, RoutePreviewServiceImpl.MAX_ROUTE_POINTS);
        assertThat(retry.stream().filter(RoadRoutingService.Waypoint::through).count()).isGreaterThan(1);
        verify(cache, atLeastOnce()).put(startsWith("valhalla-truck-country-v2|"), any());
    }

    @Test
    void internationalVietnamLegAvoidsCambodiaAndLaosBeforeTheSelectedGate() {
        Location gate = location("BorderGate", "Lao Bảo", "VN", LAO_BAO);
        gate.setDestinationCountry("LA");
        RoadRouteDto foreign = road(List.of(HCM, PHNOM_PENH, SAVANNAKHET, LAO_BAO), List.of(LAO_BAO, SAVANNAKHET));
        RoadRouteDto valid = road(List.of(HCM, VIETNAM.get(1), VIETNAM.get(2), LAO_BAO), List.of(LAO_BAO, SAVANNAKHET));
        answerByThroughPoints(foreign, valid);

        RoutePreviewResponseDto preview = service.preview(request(HCM, "VN", SAVANNAKHET, "LA", List.of(gate.getId())));

        assertThat(preview.legs()).hasSize(2);
        assertThat(preview.km()).isEqualTo(valid.km());
        assertThat(preview.borderArrivalAt()).isNotNull();
        assertThat(preview.path()).doesNotContain(new double[] {PHNOM_PENH.lat(), PHNOM_PENH.lng()});
    }

    @Test
    void destinationLegCannotTransitThailand() {
        Location gate = location("BorderGate", "Lao Bảo", "VN", LAO_BAO);
        RoadRouteDto foreign = road(List.of(HCM, VIETNAM.get(1), VIETNAM.get(2), LAO_BAO),
                List.of(LAO_BAO, new GeoPointDto(13.7563, 100.5018), SAVANNAKHET));
        answerByThroughPoints(foreign, foreign);

        assertThatThrownBy(() -> service.preview(request(HCM, "VN", SAVANNAKHET, "LA", List.of(gate.getId()))))
                .isInstanceOf(DomesticRouteAbroadException.class).hasMessageContaining("Thái Lan");
    }

    @Test
    void cannotEnterDestinationCountryBeforeTheSelectedGate() {
        Location gate = location("BorderGate", "Mộc Bài", "VN", MOC_BAI);
        RoadRouteDto foreign = road(List.of(HCM, PHNOM_PENH, MOC_BAI), List.of(MOC_BAI, PHNOM_PENH));
        answerByThroughPoints(foreign, foreign);

        assertThatThrownBy(() -> service.preview(request(HCM, "VN", PHNOM_PENH, "KH", List.of(gate.getId()))))
                .isInstanceOf(DomesticRouteAbroadException.class).hasMessageContaining("Campuchia");
    }

    @Test
    void legLeavingAGatePlacedInsideVietnamMayStillCrossTheBorder() {
        // Tọa độ cửa khẩu Mộc Bài trong dữ liệu nằm phía Việt Nam, cách biên hơn 5 km
        GeoPointDto gatePoint = new GeoPointDto(11.07, 106.2);
        GeoPointDto bavet = new GeoPointDto(11.08, 106.15);
        GeoPointDto kampongCham = new GeoPointDto(12.0, 105.46);
        Location gate = location("BorderGate", "Mộc Bài", "VN", gatePoint);
        Location rest = location("RestStop", "Trạm nghỉ Kampong Cham", "KH", kampongCham);
        RoadRouteDto valid = road(List.of(HCM, gatePoint), List.of(gatePoint, bavet, kampongCham), List.of(kampongCham, PHNOM_PENH));
        answerByThroughPoints(valid, valid);

        RoutePreviewResponseDto preview = service.preview(request(HCM, "VN", PHNOM_PENH, "KH", List.of(gate.getId(), rest.getId())));

        assertThat(preview.legs()).hasSize(3);
        assertThat(preview.borderArrivalAt()).isNotNull();
    }

    @Test
    void legAfterTheCrossingLegMustStayInDestinationCountry() {
        GeoPointDto kampongCham = new GeoPointDto(12.0, 105.46);
        Location gate = location("BorderGate", "Mộc Bài", "VN", MOC_BAI);
        Location rest = location("RestStop", "Trạm nghỉ Kampong Cham", "KH", kampongCham);
        RoadRouteDto foreign = road(List.of(HCM, MOC_BAI), List.of(MOC_BAI, kampongCham), List.of(kampongCham, HCM, PHNOM_PENH));
        answerByThroughPoints(foreign, foreign);

        assertThatThrownBy(() -> service.preview(request(HCM, "VN", PHNOM_PENH, "KH", List.of(gate.getId(), rest.getId()))))
                .isInstanceOf(DomesticRouteAbroadException.class).hasMessageContaining("Việt Nam");
    }

    @Test
    void validCrossBorderRouteRemainsAccepted() {
        Location gate = location("BorderGate", "Mộc Bài", "VN", MOC_BAI);
        RoadRouteDto valid = road(List.of(HCM, MOC_BAI), List.of(MOC_BAI, PHNOM_PENH));
        answerByThroughPoints(valid, valid);

        RoutePreviewResponseDto preview = service.preview(request(HCM, "VN", PHNOM_PENH, "KH", List.of(gate.getId())));

        assertThat(preview.legs()).hasSize(2);
        assertThat(preview.borderArrivalAt()).isNotNull();
        verify(routing, times(1)).truckRouteVia(anyList(), anyDouble(), anyInt(), any());
    }

    @Test
    void aStopInThirdCountryIsRejectedBeforeRouting() {
        Location gate = location("BorderGate", "Mộc Bài", "VN", MOC_BAI);
        Location third = location("RestStop", "Trạm Lào", "LA", SAVANNAKHET);

        assertThatThrownBy(() -> service.preview(request(HCM, "VN", PHNOM_PENH, "KH", List.of(gate.getId(), third.getId()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nước thứ ba");
        verifyNoInteractions(routing);
    }

    @Test
    void aGateForTheWrongDestinationCountryIsRejectedBeforeRouting() {
        Location gate = location("BorderGate", "Lao Bảo", "VN", LAO_BAO);
        gate.setDestinationCountry("LA");

        assertThatThrownBy(() -> service.preview(request(HCM, "VN", PHNOM_PENH, "KH", List.of(gate.getId()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cửa khẩu");
        verifyNoInteractions(routing);
    }

    @Test
    void unreachableDetourStationDoesNotPreventTryingVietnamCorridor() {
        location("RestStop", "Trạm nghỉ", "VN", new GeoPointDto(13.782, 109.219));
        RoadRouteDto foreign = road(List.of(HCM, PHNOM_PENH, HANOI));
        RoadRouteDto valid = road(VIETNAM);
        when(routing.truckRouteVia(anyList(), anyDouble(), anyInt(), any())).thenAnswer(invocation -> {
            List<RoadRoutingService.Waypoint> points = invocation.getArgument(0);
            if (points.size() == 3) throw new NoTruckRouteException();
            return points.size() == 2 ? foreign : valid;
        });

        assertThat(service.preview(request(HCM, "VN", HANOI, "VN", List.of())).km()).isEqualTo(valid.km());
        verify(routing, times(3)).truckRouteVia(anyList(), anyDouble(), anyInt(), any());
    }

    @Test
    void checksBetweenSparseShapePointsRatherThanOnlyTheirEndpoints() {
        // Đường thẳng nối HCM và Hà Nội cắt qua Campuchia/Lào dù cả hai đỉnh đều ở Việt Nam.
        RoadRouteDto sparse = road(List.of(HCM, HANOI));
        answerByThroughPoints(sparse, sparse);

        assertThatThrownBy(() -> service.preview(request(HCM, "VN", HANOI, "VN", List.of())))
                .isInstanceOf(DomesticRouteAbroadException.class).hasMessageContaining("Campuchia");
    }

    @Test
    void movesEvenASingleForeignStationAfterTheGate() {
        Location gate = location("BorderGate", "Mộc Bài", "VN", MOC_BAI);
        GeoPointDto stationPoint = new GeoPointDto(11.5, 105.3);
        Location station = location("RestStop", "Trạm Campuchia", "KH", stationPoint);
        RoadRouteDto base = road(List.of(HCM, MOC_BAI), List.of(MOC_BAI, PHNOM_PENH));
        RoadRouteDto valid = road(List.of(HCM, MOC_BAI), List.of(MOC_BAI, stationPoint), List.of(stationPoint, PHNOM_PENH));
        when(routing.truckRouteVia(anyList(), anyDouble(), anyInt(), any())).thenAnswer(invocation -> {
            List<RoadRoutingService.Waypoint> points = invocation.getArgument(0);
            return points.size() == 3 ? base : valid;
        });

        RoutePreviewResponseDto preview = service.preview(request(HCM, "VN", PHNOM_PENH, "KH", List.of(station.getId(), gate.getId())));

        assertThat(preview.legs()).extracting(RoutePreviewResponseDto.Leg::to)
                .containsExactly("Mộc Bài", "Trạm Campuchia", "Đích");
    }

    private void answerByThroughPoints(RoadRouteDto base, RoadRouteDto retry) {
        when(routing.truckRouteVia(anyList(), anyDouble(), anyInt(), any())).thenAnswer(invocation -> {
            List<RoadRoutingService.Waypoint> points = invocation.getArgument(0);
            return points.stream().anyMatch(RoadRoutingService.Waypoint::through) ? retry : base;
        });
    }

    private Location location(String type, String name, String country, GeoPointDto point) {
        Location location = new Location();
        location.setId(catalog.size() + 1);
        location.setLocationType(type);
        location.setLocationName(name);
        location.setCountry(country);
        location.setLatitude(point.lat());
        location.setLongitude(point.lng());
        location.setStatus("ACTIVE");
        catalog.add(location);
        return location;
    }

    private static RoutePreviewRequestDto request(GeoPointDto from, String fromCountry, GeoPointDto to,
                                                 String toCountry, List<Integer> stops) {
        return new RoutePreviewRequestDto(new RoutePlaceDto("Đón", from.lat(), from.lng(), fromCountry),
                new RoutePlaceDto("Đích", to.lat(), to.lng(), toCountry), stops, "truck",
                Instant.parse("2026-11-20T22:00:00Z").toEpochMilli());
    }

    @SafeVarargs
    private static RoadRouteDto road(List<GeoPointDto>... shapes) {
        List<RoadRouteDto.Leg> legs = new ArrayList<>();
        for (List<GeoPointDto> shape : shapes) {
            legs.add(new RoadRouteDto.Leg(100, 3600, shape));
        }
        return new RoadRouteDto(100 * legs.size(), 3600L * legs.size(), legs);
    }
}
