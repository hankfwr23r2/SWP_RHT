package com.example.racehorse_transport.dto.coordinator;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoordinatorPlanRequest {
    private String by;
    private List<VehicleTripDto> trips;
    private Object route;
    private String gate;
    private String note;
}
