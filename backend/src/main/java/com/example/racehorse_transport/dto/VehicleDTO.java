package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDTO implements Serializable {
    private Integer id;
    private String vehicleType;
    private String licensePlate;
    private Integer capacity;
    private String status;
}
