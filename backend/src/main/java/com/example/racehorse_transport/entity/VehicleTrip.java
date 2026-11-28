package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.VehicleEndCondition;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;

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

import java.time.Instant;

// Một lượt chạy thật của một xe. Lịch thương mại nằm ở departure; một chuyến có thể cần nhiều lượt xe.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vehicle_trip")
public class VehicleTrip {

    @Column(name = "delivery_window_warned_at")
    private Instant deliveryWindowWarnedAt;

    @Column(name = "delivery_ride_warned_at")
    private Instant deliveryRideWarnedAt;

    @Column(name = "return_late_notified_at")
    private Instant returnLateNotifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coordinator_id")
    private Staff coordinator;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", length = 30, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", length = 30, nullable = false)
    private VehicleTripType tripType;

    // Bắt buộc khi LINEHAUL (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "departure_id")
    private Departure departure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    // Phải là nơi xe kết thúc lượt chạy trước đó (kiểm ở service)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "start_location_id", nullable = false)
    private Location startLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "end_location_id", nullable = false)
    private Location endLocation;

    @Column(name = "planned_start_at", nullable = false)
    private Instant plannedStartAt;

    @Column(name = "planned_end_at", nullable = false)
    private Instant plannedEndAt;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_vehicle_condition", length = 30)
    private VehicleEndCondition endVehicleCondition;

    @Column(name = "end_vehicle_note", length = 1000)
    private String endVehicleNote;

    // Niêm phong và khử trùng chỉ ghi nhận, đợt này chưa có quy tắc kiểm tra
    @Column(name = "sealed_at")
    private Instant sealedAt;

    @Column(name = "disinfected_at")
    private Instant disinfectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private VehicleTripStatus status;
}
