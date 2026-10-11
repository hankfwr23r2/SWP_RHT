package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

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

    @Size(max = 50)
    @Column(name = "vehicle_code", length = 50)
    private String vehicleCode;

    @Size(max = 255)
    @Nationalized
    @Column(name = "vehicle_name")
    private String vehicleName;

    @Size(max = 100)
    @Column(name = "vehicle_type", length = 100)
    private String vehicleType;

    @Size(max = 50)
    @Column(name = "license_plate", length = 50)
    private String licensePlate;

    @Column(name = "capacity")
    private Integer capacity;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;

    @Size(max = 255)
    @Nationalized
    @Column(name = "home_depot")
    private String homeDepot;

    @Size(max = 255)
    @Nationalized
    @Column(name = "current_location")
    private String currentLocation;
}