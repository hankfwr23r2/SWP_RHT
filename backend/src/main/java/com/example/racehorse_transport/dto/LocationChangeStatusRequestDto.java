package com.example.racehorse_transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "LocationChangeStatusRequest")
public class LocationChangeStatusRequestDto {
    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE")
    private String status;
}
