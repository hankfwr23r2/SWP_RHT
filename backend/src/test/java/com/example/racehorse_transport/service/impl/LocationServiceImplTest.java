package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.CreateRestStopRequest;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.LocationRouteRepository;
import com.example.racehorse_transport.repository.RegulationRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationServiceImplTest {

    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final LocationRouteRepository locationRouteRepository = mock(LocationRouteRepository.class);
    private final RegulationRepository regulationRepository = mock(RegulationRepository.class);
    private final LocationServiceImpl service = new LocationServiceImpl(
            locationRepository, locationRouteRepository, regulationRepository);

    @Test
    void createRestStopSetsTypeAndTrimsFields() {
        when(locationRepository.findMaxId()).thenReturn(12);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location created = service.createRestStop(request());

        assertThat(created.getId()).isEqualTo(13);
        assertThat(created.getLocationType()).isEqualTo("RestStop");
        assertThat(created.getLocationName()).isEqualTo("Trạm nghỉ");
        verify(locationRepository).save(created);
    }

    @Test
    void updateRestStopDoesNotAllowEditingOtherLocationTypes() {
        Location warehouse = location(3, "Warehouse");
        when(locationRepository.findById(3)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> service.updateRestStop(3, request()))
                .isInstanceOf(com.example.racehorse_transport.exception.ResourceNotFoundException.class);

        verify(locationRepository, never()).save(any(Location.class));
    }

    @Test
    void deleteRestStopRejectsLocationsAssignedToRoutes() {
        Location restStop = location(5, "RestStop");
        when(locationRepository.findById(5)).thenReturn(Optional.of(restStop));
        when(locationRouteRepository.existsByLocationID_Id(5)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteRestStop(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đang được sử dụng trên tuyến đường");

        verify(locationRepository, never()).delete(any(Location.class));
    }

    @Test
    void deleteRestStopRejectsLocationsUsedByRegulations() {
        Location restStop = location(6, "RestStop");
        when(locationRepository.findById(6)).thenReturn(Optional.of(restStop));
        when(regulationRepository.existsByLocationID_Id(6)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteRestStop(6))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đang được sử dụng trong quy định");

        verify(locationRepository, never()).delete(any(Location.class));
    }

    @Test
    void deleteRestStopRemovesUnusedLocation() {
        Location restStop = location(7, "RestStop");
        when(locationRepository.findById(7)).thenReturn(Optional.of(restStop));

        service.deleteRestStop(7);

        verify(locationRepository).delete(restStop);
    }

    private CreateRestStopRequest request() {
        return CreateRestStopRequest.builder()
                .locationName(" Trạm nghỉ ")
                .address(" Quốc lộ 1 ")
                .city(" Đà Nẵng ")
                .province(" Đà Nẵng ")
                .country(" Việt Nam ")
                .build();
    }

    private Location location(Integer id, String type) {
        Location location = new Location();
        location.setId(id);
        location.setLocationType(type);
        return location;
    }
}
