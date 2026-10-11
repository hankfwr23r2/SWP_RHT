package com.example.racehorse_transport.dto.coordinator;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTripDto {
    private String tripId;
    private String vehicleId;
    private String driverId;
    private String escortId;
    private List<String> horseIds;
}
