package com.example.racehorse_transport.dto.coordinator;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CoordinatorPlanRequest")
public class CoordinatorPlanRequestDto {
    private String by;
    private Integer routeId;
    private List<VehicleTripDto> trips;
    private Object route;
    private String gate;
    private String note;
}
