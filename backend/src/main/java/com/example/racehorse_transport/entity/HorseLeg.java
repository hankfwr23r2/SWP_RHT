package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;

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

// Một chặng di chuyển của một con ngựa. Chặng do xe công ty chở (vehicleTrip) hoặc do đại lý đảm nhận (agentParty).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "horse_leg")
public class HorseLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_horse_id", nullable = false)
    private BookingHorse bookingHorse;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "leg_type", length = 30, nullable = false)
    private HorseLegType legType;

    // Điểm đi và điểm đến: location của mạng lưới hoặc địa chỉ của khách, mỗi đầu đúng một (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "from_location_id")
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "from_booking_address_id")
    private BookingAddress fromBookingAddress;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "to_location_id")
    private Location toLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "to_booking_address_id")
    private BookingAddress toBookingAddress;

    // Xe công ty chở hoặc đại lý chở, đúng một (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vehicle_trip_id")
    private VehicleTrip vehicleTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "agent_party_id")
    private Party agentParty;

    // Lên và xuống xe ở điểm dừng nào. Chặng của đại lý thì để trống
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "board_trip_stop_id")
    private TripStop boardTripStop;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "alight_trip_stop_id")
    private TripStop alightTripStop;

    // Chặng thất bại mà chặng này thay thế, ví dụ giao lại lần hai
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "replaced_horse_leg_id")
    private HorseLeg replacedHorseLeg;

    @Column(name = "planned_start_at", nullable = false)
    private Instant plannedStartAt;

    @Column(name = "planned_end_at", nullable = false)
    private Instant plannedEndAt;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private HorseLegStatus status;
}
