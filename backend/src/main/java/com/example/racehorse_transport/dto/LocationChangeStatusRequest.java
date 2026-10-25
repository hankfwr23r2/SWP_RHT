package com.example.racehorse_transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocationChangeStatusRequest {
    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE")
    private String status;
}
