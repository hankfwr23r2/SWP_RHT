package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.VehicleStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

// Xe. Sức chứa lấy từ cột số khoang, không đọc từ chuỗi.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "license_plate", length = 20, nullable = false)
    private String licensePlate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_class_id", nullable = false)
    private VehicleClass vehicleClass;

    @Column(name = "standard_stalls", nullable = false)
    private Integer standardStalls;

    @Column(name = "wide_stalls", nullable = false)
    private Integer wideStalls;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_hub_id", nullable = false)
    private Location homeHub;

    @Column(name = "registration_expiry")
    private LocalDate registrationExpiry;

    // Xe đang chạy hay rảnh suy ra từ vehicle_trip, không lưu ở đây
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private VehicleStatus status;
}
