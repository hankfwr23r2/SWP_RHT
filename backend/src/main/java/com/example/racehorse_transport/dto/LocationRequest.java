package com.example.racehorse_transport.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocationRequest {
    @NotBlank
    @Pattern(regexp = "Warehouse|RidingClub|RaceTrack|Stable|RestStop|BorderGate|RescuePoint|City")
    private String locationType;

    @NotBlank
    @Size(max = 255)
    private String locationName;

    @NotBlank
    @Size(max = 255)
    private String address;

    @NotBlank
    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(max = 100)
    private String province;

    @NotBlank
    @Size(max = 100)
    private String country;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @NotBlank
    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE")
    private String status = "ACTIVE";

    @Min(0)
    @Max(1439)
    private Integer openingMinutes;

    @Min(1)
    @Max(1440)
    private Integer closingMinutes;

    @Size(max = 100)
    private String contactPhone;

    @Pattern(regexp = "LA|KH")
    private String destinationCountry;
}
