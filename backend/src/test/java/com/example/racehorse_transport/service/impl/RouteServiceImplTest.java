package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.route.CreateRouteRequest;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.LocationRouteRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteServiceImplTest {

    private final RouteRepository routeRepository = mock(RouteRepository.class);
    private final LocationRouteRepository locationRouteRepository = mock(LocationRouteRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final RouteServiceImpl service = new RouteServiceImpl(
            routeRepository, locationRouteRepository, locationRepository);

    @BeforeEach
    void setUp() {
        when(routeRepository.findMaxId()).thenReturn(4);
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationRepository.findById(anyInt())).thenAnswer(invocation ->
                Optional.of(location(invocation.getArgument(0))));
    }

    @Test
    void createRouteRequiresDistinctOriginAndDestination() {
        CreateRouteRequest request = request(List.of(stop(1, null, null)));

        assertThatThrownBy(() -> service.createRoute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ít nhất điểm xuất phát");

        verify(routeRepository, never()).save(any(Route.class));
    }

    @Test
    void createRoutePersistsOrderedStopsAndCoordinates() {
        CreateRouteRequest request = request(List.of(
                stop(1, 10.78, 107.0),
                stop(2, 11.07, 106.2),
                stop(3, 11.56, 104.92)));

        Route created = service.createRoute(request);

        ArgumentCaptor<List<LocationRoute>> stops = ArgumentCaptor.forClass(List.class);
        verify(locationRouteRepository).saveAll(stops.capture());
        assertThat(created.getId()).isEqualTo(5);
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(stops.getValue()).extracting(LocationRoute::getStopOrder).containsExactly(1, 2, 3);
        assertThat(stops.getValue()).extracting(LocationRoute::getLatitude)
                .containsExactly(10.78, 11.07, 11.56);
    }

    @Test
    void updateRouteAcceptsMaintenanceStatus() {
        Route existing = new Route();
        existing.setId(2);
        existing.setRouteName("Tuyến cũ");
        when(routeRepository.findById(2)).thenReturn(Optional.of(existing));

        CreateRouteRequest request = new CreateRouteRequest();
        request.setStatus("maintenance");

        Route updated = service.updateRoute(2, request);

        assertThat(updated.getStatus()).isEqualTo("MAINTENANCE");
    }

    @Test
    void createRouteRejectsRepeatedLocation() {
        CreateRouteRequest request = request(List.of(stop(1, null, null), stop(1, null, null)));

        assertThatThrownBy(() -> service.createRoute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thể xuất hiện nhiều lần");

        verify(routeRepository, never()).save(any(Route.class));
    }

    private CreateRouteRequest request(List<CreateRouteRequest.RouteStopItem> stops) {
        CreateRouteRequest request = new CreateRouteRequest();
        request.setRouteName("Tuyến thử");
        request.setStops(stops);
        return request;
    }

    private CreateRouteRequest.RouteStopItem stop(Integer locationId, Double latitude, Double longitude) {
        return CreateRouteRequest.RouteStopItem.builder()
                .locationId(locationId)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    private Location location(Integer id) {
        Location location = new Location();
        location.setId(id);
        return location;
    }
}
