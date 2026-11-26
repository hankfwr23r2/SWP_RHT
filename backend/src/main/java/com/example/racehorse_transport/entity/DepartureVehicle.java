package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.DepartureAllotmentType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "departure_vehicle")
@Getter
@Setter
public class DepartureVehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departure_id", nullable = false)
    private Departure departure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_class_id", nullable = false)
    private VehicleClass vehicleClass;

    @Column(name = "vehicle_count", nullable = false)
    private int vehicleCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "allotment_type", length = 30, nullable = false)
    private DepartureAllotmentType allotmentType;

    @Column(length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
