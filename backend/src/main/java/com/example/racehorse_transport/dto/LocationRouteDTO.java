package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.time.LocalTime;
import com.example.racehorse_transport.entity.LocationRouteId;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocationRouteDTO implements Serializable {
    private LocationRouteId id;
    private Integer routeID;
    private Integer locationID;
    private Integer stopOrder;
    private LocalTime arrivalTime;
    private LocalTime departureTime;
}
