package com.example.racehorse_transport.dto.route;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "LocationRouteDTO")
public class LocationRouteDto implements Serializable {
    private LocationRouteId id;
    private Integer routeID;
    private Integer locationID;
    private Integer stopOrder;
    private LocalTime arrivalTime;
    private LocalTime departureTime;

    private String locationName;
    private String locationType;
    private String address;
    private String province;
    private String country;
    private Double latitude;
    private Double longitude;
}
