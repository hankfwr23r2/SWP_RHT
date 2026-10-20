package com.example.racehorse_transport.dto.fleet;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleRequest {
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
}
