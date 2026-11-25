package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus;
import com.example.racehorse_transport.entity.enums.HealthCheckType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "health_check_task")
@NamedEntityGraph(name = "HealthCheckTask.details", attributeNodes = {
        @NamedAttributeNode(value = "bookingHorse", subgraph = "horse"),
        @NamedAttributeNode(value = "horseStay", subgraph = "stay"),
        @NamedAttributeNode("vehicleTrip"),
        @NamedAttributeNode("assignedTo"),
        @NamedAttributeNode("assignedBy"),
        @NamedAttributeNode("healthLog")
}, subgraphs = {
        @NamedSubgraph(name = "horse", attributeNodes = @NamedAttributeNode("horse")),
        @NamedSubgraph(name = "stay", attributeNodes = @NamedAttributeNode("location"))
})
public class HealthCheckTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_horse_id", nullable = false)
    private BookingHorse bookingHorse;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vehicle_trip_id", nullable = true)
    private VehicleTrip vehicleTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "horse_stay_id", nullable = true)
    private HorseStay horseStay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_to", nullable = false)
    private Staff assignedTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private Staff assignedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "health_log_id", nullable = true)
    private HorseHealthLog healthLog;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private HealthCheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private HealthCheckTaskStatus status;

    @Column(nullable = false)
    private Instant assignedAt;

    @Column(nullable = false)
    private Instant dueAt;

    @Column(length = 500)
    private String note;

    private Instant acceptedAt;
    private Instant doneAt;

    @Column(length = 500)
    private String cancelReason;

    private Instant overdueNotifiedAt;
}
