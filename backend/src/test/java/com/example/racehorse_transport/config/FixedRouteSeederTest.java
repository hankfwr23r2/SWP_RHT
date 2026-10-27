package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.LocationRouteRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FixedRouteSeederTest {

    @Test
    void seedsRoutesOnceAndPreservesOrderedStops() {
        RouteRepository routeRepository = mock(RouteRepository.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        LocationRouteRepository locationRouteRepository = mock(LocationRouteRepository.class);
        AtomicInteger routeIds = new AtomicInteger(10);
        AtomicInteger locationIds = new AtomicInteger(100);
        Map<String, Route> routes = new HashMap<>();
        Map<String, Route> routesBySeedKey = new HashMap<>();
        Map<String, Location> locations = new HashMap<>();
        List<List<LocationRoute>> savedStops = new ArrayList<>();

        when(routeRepository.findByRouteNameIgnoreCase(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(routes.get(invocation.getArgument(0))));
        when(routeRepository.findBySeedKey(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(routesBySeedKey.get(invocation.getArgument(0))));
        when(routeRepository.findMaxId()).thenAnswer(invocation -> routeIds.get());
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> {
            Route route = invocation.getArgument(0);
            routeIds.set(Math.max(routeIds.get(), route.getId()));
            routes.put(route.getRouteName(), route);
            routesBySeedKey.put(route.getSeedKey(), route);
            return route;
        });
        when(locationRepository.findMaxId()).thenAnswer(invocation -> locationIds.get());
        when(locationRepository.findByLocationCodeIgnoreCase(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return Optional.of(locations.computeIfAbsent(code, key -> {
                Location location = new Location();
                location.setId(locationIds.incrementAndGet());
                location.setLocationCode(key);
                location.setLatitude(10.0);
                location.setLongitude(106.0);
                return location;
            }));
        });
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationRouteRepository.saveAll(any())).thenAnswer(invocation -> {
            savedStops.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        FixedRouteSeeder seeder = new FixedRouteSeeder(routeRepository, locationRepository, locationRouteRepository);
        seeder.run();
        routes.values().iterator().next().setRouteName("Admin renamed route");
        seeder.run();

        verify(routeRepository, times(3)).save(any(Route.class));
        assertThat(routes).hasSize(3);
        assertThat(routesBySeedKey.values()).extracting(Route::getRouteName).contains("Admin renamed route");
        assertThat(savedStops).hasSize(3);
        ArgumentCaptor<List<LocationRoute>> captor = ArgumentCaptor.forClass(List.class);
        verify(locationRouteRepository, times(3)).saveAll(captor.capture());
        assertThat(captor.getAllValues().get(0)).extracting(LocationRoute::getStopOrder)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(captor.getAllValues().get(1)).extracting(LocationRoute::getStopOrder)
                .containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void renamesExistingSeedRoutesOnlyWhenTheirNamesStillMatchTheOldDefaults() {
        RouteRepository routeRepository = mock(RouteRepository.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        LocationRouteRepository locationRouteRepository = mock(LocationRouteRepository.class);
        Map<String, Route> routesBySeedKey = new HashMap<>();
        routesBySeedKey.put("starter-vn-central", route("starter-vn-central", "Mẫu nội địa: TP.HCM – Đà Nẵng"));
        routesBySeedKey.put("starter-vn-cambodia-mocbai", route("starter-vn-cambodia-mocbai", "Mẫu quốc tế: TP.HCM – Phnom Penh qua Mộc Bài"));
        routesBySeedKey.put("starter-vn-laos-laobao", route("starter-vn-laos-laobao", "Mẫu quốc tế: TP.HCM – Viêng Chăn qua Lao Bảo"));

        when(routeRepository.findBySeedKey(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(routesBySeedKey.get(invocation.getArgument(0))));
        when(locationRepository.findByLocationCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(locationRepository.findMaxId()).thenReturn(100);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FixedRouteSeeder seeder = new FixedRouteSeeder(routeRepository, locationRepository, locationRouteRepository);
        seeder.run();

        assertThat(routesBySeedKey.values()).extracting(Route::getRouteName).containsExactlyInAnyOrder(
                "Tuyến nội địa: TP.HCM – Đà Nẵng",
                "Tuyến quốc tế: TP.HCM – Phnom Penh qua Mộc Bài",
                "Tuyến quốc tế: TP.HCM – Viêng Chăn qua Lao Bảo");
        verify(routeRepository, times(3)).save(any(Route.class));
        verify(locationRouteRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    private static Route route(String seedKey, String routeName) {
        Route route = new Route();
        route.setSeedKey(seedKey);
        route.setRouteName(routeName);
        return route;
    }
}
