package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.LocationRequest;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.service.AdminAuditWriter;
import com.example.racehorse_transport.repository.LocationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationServiceImplTest {
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final AdminAuditWriter audit = mock(AdminAuditWriter.class);
    private final LocationServiceImpl service = new LocationServiceImpl(audit, locationRepository);

    @Test
    void createLocationTrimsFieldsAndAuditsValues() {
        when(locationRepository.findMaxId()).thenReturn(12);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location created = service.createLocation(request("RestStop"), "admin");

        assertThat(created.getId()).isEqualTo(13);
        assertThat(created.getLocationCode()).isEqualTo("LOC-13");
        assertThat(created.getLocationType()).isEqualTo("RestStop");
        assertThat(created.getLocationName()).isEqualTo("Trạm nghỉ");
        assertThat(created.getLatitude()).isEqualTo(10.75);
        verify(audit).record(org.mockito.ArgumentMatchers.eq("CREATE_LOCATION"), any(), any(), any(), any(), org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void updateLocationRejectsBorderGateWithoutValidWindow() {
        LocationRequest request = request("BorderGate");
        request.setOpeningMinutes(990);
        request.setClosingMinutes(450);
        request.setDestinationCountry("KH");

        assertThatThrownBy(() -> service.updateLocation(1, request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Giờ mở cửa");

        verify(locationRepository, never()).findById(1);
    }

    @Test
    void updateLocationRecordsOldAndNewFieldValues() {
        Location existing = location(5, "RestStop");
        existing.setLocationName("Trạm cũ");
        existing.setLatitude(10.0);
        when(locationRepository.findById(5)).thenReturn(Optional.of(existing));
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location updated = service.updateLocation(5, request("RestStop"), "admin");

        assertThat(updated.getLocationName()).isEqualTo("Trạm nghỉ");
        verify(audit).record(org.mockito.ArgumentMatchers.eq("UPDATE_LOCATION"), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(before -> "Trạm cũ".equals(before.get("locationName"))),
                org.mockito.ArgumentMatchers.argThat(after -> "Trạm nghỉ".equals(after.get("locationName"))));
    }

    @Test
    void deactivateLocationKeepsRecordAndLogsStatusChange() {
        Location existing = location(7, "RescuePoint");
        when(locationRepository.findById(7)).thenReturn(Optional.of(existing));
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location updated = service.changeStatus(7, "INACTIVE", "admin");

        assertThat(updated.getStatus()).isEqualTo("INACTIVE");
        verify(locationRepository, never()).delete(any(Location.class));
        verify(audit).record(org.mockito.ArgumentMatchers.eq("CHANGE_LOCATION_STATUS"), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(before -> "ACTIVE".equals(before.get("status"))),
                org.mockito.ArgumentMatchers.argThat(after -> "INACTIVE".equals(after.get("status"))));
    }

    @Test
    void publicLocationListExcludesInactiveRecords() {
        Location active = location(1, "Warehouse");
        Location inactive = location(2, "Warehouse");
        inactive.setStatus("INACTIVE");
        when(locationRepository.findAll()).thenReturn(List.of(active, inactive));

        assertThat(service.findLocations(null, null, false)).containsExactly(active);
        assertThat(service.findLocations(null, null, true)).containsExactly(active, inactive);
    }

    private LocationRequest request(String type) {
        LocationRequest request = new LocationRequest();
        request.setLocationType(type);
        request.setLocationName(" Trạm nghỉ ");
        request.setAddress(" Quốc lộ 1 ");
        request.setCity(" Đà Nẵng ");
        request.setProvince(" Đà Nẵng ");
        request.setCountry(" Việt Nam ");
        request.setLatitude(10.75);
        request.setLongitude(106.7);
        request.setStatus("ACTIVE");
        if ("BorderGate".equals(type)) {
            request.setOpeningMinutes(450);
            request.setClosingMinutes(990);
            request.setDestinationCountry("KH");
        }
        return request;
    }

    private Location location(Integer id, String type) {
        Location location = new Location();
        location.setId(id);
        location.setLocationCode("LOC-" + id);
        location.setLocationType(type);
        location.setLocationName("Vị trí");
        location.setAddress("Address");
        location.setCity("City");
        location.setProvince("Province");
        location.setCountry("Country");
        location.setLatitude(10.0);
        location.setLongitude(106.0);
        location.setStatus("ACTIVE");
        return location;
    }
}
