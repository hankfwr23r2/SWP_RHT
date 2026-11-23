package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.TripStopType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Điểm dừng của một lượt chạy: vừa là lộ trình kế hoạch vừa ghi giờ thực tế.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "trip_stop")
public class TripStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_trip_id", nullable = false)
    private VehicleTrip vehicleTrip;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    // Điểm dừng là location của mạng lưới hoặc địa chỉ của khách, đúng một trong hai (CHECK ở DB)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "booking_address_id")
    private BookingAddress bookingAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", length = 30, nullable = false)
    private TripStopType stopType;

    // Điểm này chép từ điểm nào của tuyến (FK ghép tới route_stop). NULL với điểm đón, giao của khách
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "route_id", referencedColumnName = "route_id"),
            @JoinColumn(name = "route_stop_sequence_number", referencedColumnName = "sequence_number")
    })
    private RouteStop routeStop;

    // snapshot: giờ kế hoạch chép từ route_stop lúc lập lượt xe, không đổi theo tuyến
    @Column(name = "planned_arrival_at", nullable = false)
    private Instant plannedArrivalAt;

    @Column(name = "planned_departure_at", nullable = false)
    private Instant plannedDepartureAt;

    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;

    @Column(name = "actual_departure_at")
    private Instant actualDepartureAt;
}
