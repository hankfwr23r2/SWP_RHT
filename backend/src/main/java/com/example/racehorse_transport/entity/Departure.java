package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.DepartureStatus;

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
import java.time.LocalDate;

// Chuyến cụ thể theo ngày. Khách đặt chỗ vào đây.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "departure")
public class Departure {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coordinator_id")
    private Staff coordinator;

    @Column(name = "coordinator_reminded_on")
    private LocalDate coordinatorRemindedOn;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // NULL = chuyến đột xuất
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "departure_schedule_id")
    private DepartureSchedule departureSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_departure_id")
    private Departure mergedIntoDeparture;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "planned_departure_at", nullable = false)
    private Instant plannedDepartureAt;

    @Column(name = "revised_departure_at")
    private Instant revisedDepartureAt;

    @Column(name = "run_reminded_at")
    private Instant runRemindedAt;

    @Column(name = "delayed_at")
    private Instant delayedAt;

    @Column(name = "delay_reason", length = 500)
    private String delayReason;

    @Column(name = "delay_escalated_at")
    private Instant delayEscalatedAt;

    @Column(name = "auto_cancelled_at")
    private Instant autoCancelledAt;

    @Column(name = "booking_cutoff_at", nullable = false)
    private Instant bookingCutoffAt;

    // snapshot: chép từ lịch, sửa được cho từng chuyến
    // derived: DepartureServiceImpl
    @Column(name = "stall_capacity", nullable = false)
    private Integer stallCapacity;

    // derived: DepartureService (chỉ ghi khi đang giữ SELECT ... FOR UPDATE)
    @Column(name = "booked_stall_count", nullable = false)
    private Integer bookedStallCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private DepartureStatus status;

    // Quản lý xác nhận chạy từ mốc quyết định; NULL = chưa xác nhận
    @Column(name = "run_confirmed_at")
    private Instant runConfirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_confirmed_by")
    private AppUser runConfirmedBy;
}
