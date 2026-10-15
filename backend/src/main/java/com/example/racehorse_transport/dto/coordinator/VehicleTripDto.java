package com.example.racehorse_transport.dto.coordinator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleTripDto {
    private String tripId;
    private String vehicleId;
    private String driverId;
    private String escortId;
    private List<String> horseIds;
    private Map<String, Object> acks;
    private Long departedAt;
    private Map<String, Object> driverPack;
    private Map<String, Object> run;
    private Map<String, Object> homeReturn;
}
