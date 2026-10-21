package com.example.racehorse_transport.dto.fleet;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {
    private Integer id;
    private String code;
    private String name;
    private String type;
    private String plate;
    private Integer capacity;
    private String status;
    private String homeDepot;
    private String currentLocation;
    private boolean isAvailable;
    private String busyReason;
}
