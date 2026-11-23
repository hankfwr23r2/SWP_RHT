package com.example.racehorse_transport.dto.vehicle;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record VehicleCreateRequestDto(
        @NotBlank @Size(max = 20) String licensePlate,
        @NotNull @Positive Long vehicleClassId,
        @NotNull @PositiveOrZero Integer standardStallCount,
        @NotNull @PositiveOrZero Integer wideStallCount,
        @NotNull @Positive Long homeHubLocationId) {

    public String normalizedLicensePlate() {
        return licensePlate.strip().toUpperCase(Locale.ROOT);
    }

    public Vehicle toEntity(VehicleClass vehicleClass, Location homeHubLocation) {
        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate(normalizedLicensePlate());
        vehicle.setVehicleClass(vehicleClass);
        vehicle.setStandardStallCount(standardStallCount);
        vehicle.setWideStallCount(wideStallCount);
        vehicle.setHomeHubLocation(homeHubLocation);
        return vehicle;
    }
}
