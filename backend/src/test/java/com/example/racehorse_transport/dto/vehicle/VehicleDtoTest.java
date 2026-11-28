package com.example.racehorse_transport.dto.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleClass;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class VehicleDtoTest {

    @Test
    void createsVehicleWithResolvedReferencesAndWithoutServerFields() {
        VehicleClass vehicleClass = new VehicleClass();
        Location headquarters = new Location();
        LocalDate expiry = LocalDate.of(2028, 3, 4);
        var request = new VehicleCreateRequestDto("  51a-12345 ", 2L, 2, 1, 3L, expiry);

        Vehicle vehicle = request.toEntity(vehicleClass, headquarters);

        assertThat(vehicle.getId()).isNull();
        assertThat(vehicle.getStatus()).isNull();
        assertThat(vehicle.getLicensePlate()).isEqualTo("51A-12345");
        assertThat(vehicle.getVehicleClass()).isSameAs(vehicleClass);
        assertThat(vehicle.getHomeHubLocation()).isSameAs(headquarters);
        assertThat(vehicle.getStandardStallCount()).isEqualTo(2);
        assertThat(vehicle.getWideStallCount()).isEqualTo(1);
        assertThat(vehicle.getRegistrationExpiryDate()).isEqualTo(expiry);
    }

    @Test
    void updatesExistingVehicleAndRetainsServerFields() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(42L);
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        vehicle.setRegistrationExpiryDate(LocalDate.of(2028, 3, 4));
        VehicleClass vehicleClass = new VehicleClass();
        Location headquarters = new Location();
        var request = new VehicleUpdateRequestDto(" 51b-12345 ", 2L, 1, 2, 3L, null);

        request.applyTo(vehicle, vehicleClass, headquarters);

        assertThat(vehicle.getId()).isEqualTo(42L);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
        assertThat(vehicle.getLicensePlate()).isEqualTo("51B-12345");
        assertThat(vehicle.getVehicleClass()).isSameAs(vehicleClass);
        assertThat(vehicle.getHomeHubLocation()).isSameAs(headquarters);
        assertThat(vehicle.getStandardStallCount()).isEqualTo(1);
        assertThat(vehicle.getWideStallCount()).isEqualTo(2);
        assertThat(vehicle.getRegistrationExpiryDate()).isNull();
    }

    @Test
    void mapsVehicleAndClassResponses() {
        VehicleClass vehicleClass = new VehicleClass();
        vehicleClass.setId(2L);
        vehicleClass.setCode("LIGHT");
        vehicleClass.setName("Xe nhẹ");
        vehicleClass.setMaxStallCount(3);
        Location headquarters = new Location();
        headquarters.setId(3L);
        headquarters.setCode("HQ");
        Vehicle vehicle = new VehicleCreateRequestDto("51A-12345", 2L, 2, 1, 3L, null)
                .toEntity(vehicleClass, headquarters);
        vehicle.setId(7L);
        vehicle.setStatus(VehicleStatus.ACTIVE);

        assertThat(VehicleClassResponseDto.from(vehicleClass))
                .isEqualTo(new VehicleClassResponseDto(2L, "LIGHT", "Xe nhẹ", 3));
        assertThat(VehicleResponseDto.from(vehicle))
                .isEqualTo(new VehicleResponseDto(7L, "51A-12345", 2L, "LIGHT", 2, 1, 3L, "HQ", null, VehicleStatus.ACTIVE));
    }
}
