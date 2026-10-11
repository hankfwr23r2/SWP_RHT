package com.example.racehorse_transport.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInRequest {
    @NotNull(message = "routeId không được để trống")
    private Integer routeId;

    @NotNull(message = "locationId không được để trống")
    private Integer locationId;

    private String imageUrl;
    private String gpsCoords;
}