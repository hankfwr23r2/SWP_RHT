package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "VEHICLE")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VehicleID", nullable = false)
    private Integer id;

    @Size(max = 100)
    @Column(name = "vehicle_type", length = 100)
    private String vehicleType;

    @Size(max = 50)
    @Column(name = "license_plate", length = 50)
    private String licensePlate;


    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;


}