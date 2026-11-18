package com.example.racehorse_transport.dto.fleet;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.racehorse_transport.entity.Vehicle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "VehicleRequest")
public class VehicleRequestDto {
    @NotBlank
    @Size(max = 100)
    private String type;

    @NotNull
    @Min(1)
    @Max(20)
    private Integer capacity;

    @NotBlank
    @Size(max = 50)
    private String plate;

    @Size(max = 50)
    private String status;

    public Vehicle toEntity(String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleType(getType().trim());
        vehicle.setCapacity(getCapacity());
        vehicle.setLicensePlate(plate);
        vehicle.setStatus(getStatus() == null || getStatus().isBlank()
                ? "ACTIVE"
                : getStatus().trim().toUpperCase(Locale.ROOT));
        return vehicle;
    }
}
