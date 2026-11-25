package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "coordinator_assignment_history")
public class CoordinatorAssignmentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_id")
    private Departure departure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_trip_id")
    private VehicleTrip vehicleTrip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_staff_id")
    private Staff fromStaff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_staff_id", nullable = false)
    private Staff toStaff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private AppUser changedBy;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant changedAt;
}
