package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.vehicle.VehicleCreateRequestDto;
import com.example.racehorse_transport.dto.vehicle.VehicleUpdateRequestDto;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.exception.VehicleRuleException;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.VehicleClassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@Transactional
class VehicleServiceTest {

    @Autowired private VehicleService vehicles;
    @Autowired private VehicleClassRepository classes;
    @Autowired private LocationRepository locations;

    @Test
    void createsUpdatesAndReadsPersistedVehicles() {
        long classId = classes.findByCode("MEDIUM").orElseThrow().getId();
        long hubId = locations.findByCode("HUB-PNH").orElseThrow().getId();

        var created = vehicles.createVehicle(new VehicleCreateRequestDto(" a2-test-01 ", classId, 1, 0, hubId));
        var updated = vehicles.updateVehicle(created.id(), new VehicleUpdateRequestDto("A2-TEST-01", classId, 1, 1, hubId));

        assertThat(created.licensePlate()).isEqualTo("A2-TEST-01");
        assertThat(updated.status()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(updated.wideStallCount()).isEqualTo(1);
        assertThat(updated.homeHubLocationId()).isEqualTo(hubId);
        assertThat(vehicles.vehicle(created.id())).isEqualTo(updated);
        assertThat(vehicles.vehicles()).contains(updated);
        assertThat(vehicles.vehicleClasses()).anyMatch(vehicleClass -> vehicleClass.id().equals(classId));
    }

    @Test
    void rejectsNonHubAndDoesNotCreateVehicle() {
        long classId = classes.findByCode("LIGHT").orElseThrow().getId();
        long hubId = locations.findByCode("GATE-MOCBAI").orElseThrow().getId();

        assertThatThrownBy(() -> vehicles.createVehicle(
                new VehicleCreateRequestDto("A2-TEST-02", classId, 1, 0, hubId)))
                .isInstanceOfSatisfying(VehicleRuleException.class,
                        error -> assertThat(error.getField()).isEqualTo("homeHubLocationId"));
        assertThat(vehicles.vehicles()).noneMatch(vehicle -> vehicle.licensePlate().equals("A2-TEST-02"));
    }

    @Test
    void returnsNotFoundForUnknownVehicle() {
        assertThatThrownBy(() -> vehicles.vehicle(Long.MAX_VALUE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> vehicles.updateVehicle(Long.MAX_VALUE,
                new VehicleUpdateRequestDto("A2-MISSING", 1L, 1, 0, 1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
