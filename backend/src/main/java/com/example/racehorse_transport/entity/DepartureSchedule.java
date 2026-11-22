package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.ScheduleStatus;

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
import java.time.LocalTime;

// Lịch chạy định kỳ. Thứ trong tuần nằm ở departure_schedule_day.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "departure_schedule")
public class DepartureSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @Column(name = "booking_cutoff_hours", nullable = false)
    private Integer bookingCutoffHours;

    @Column(name = "capacity_stalls", nullable = false)
    private Integer capacityStalls;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ScheduleStatus status;
}
