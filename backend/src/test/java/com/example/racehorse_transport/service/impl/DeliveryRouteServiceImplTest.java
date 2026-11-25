package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.client.ValhallaClient;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.VehicleClassRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.RouteCacheService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeliveryRouteServiceImplTest {
    private final LocationRepository locations = mock(LocationRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final ValhallaClient valhalla = mock(ValhallaClient.class);
    private final RouteCacheService cache = mock(RouteCacheService.class);
    private final DeliveryRouteServiceImpl service = new DeliveryRouteServiceImpl(locations, vehicles, configs, valhalla,
            mock(VehicleClassRepository.class), cache);
    private final RoadRouteDto road = new RoadRouteDto(30, 1800, List.of());

    @BeforeEach
    void setUp() {
        var vehicle = new Vehicle();
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setStandardStallCount(9);
        vehicle.setWideStallCount(0);
        when(vehicles.findAllByOrderByLicensePlateAsc()).thenReturn(List.of(vehicle));
        var hub = new Location();
        hub.setLatitude(new BigDecimal("16.05"));
        hub.setLongitude(new BigDecimal("108.2"));
        when(locations.findById(2L)).thenReturn(Optional.of(hub));
        when(configs.getInt(anyString())).thenReturn(10);
    }

    @Test
    void asksTheRoadServiceOnceAndStoresTheResult() {
        when(cache.find(anyString(), eq(RoadRouteDto.class))).thenReturn(Optional.empty());
        when(valhalla.truckRoute(any(), anyDouble(), anyInt(), any())).thenReturn(road);

        assertThat(service.plan(2L, List.of(new GeoPointDto(15.9, 108.2)), List.of(1), 1).order()).hasSize(1);

        verify(valhalla, times(2)).truckRoute(any(), anyDouble(), anyInt(), any());
        verify(cache, times(2)).put(anyString(), eq(road));
    }

    @Test
    void usesStoredRoads() {
        when(cache.find(anyString(), eq(RoadRouteDto.class))).thenReturn(Optional.of(road));

        service.plan(2L, List.of(new GeoPointDto(15.9, 108.2)), List.of(1), 1);

        verify(valhalla, never()).truckRoute(any(), anyDouble(), anyInt(), any());
    }
}
