package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "BOOKING_VEHICLE")
public class BookingVehicle {
    @EmbeddedId
    private BookingVehicleId id;

    @MapsId("bookingID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BookingID", nullable = false)
    private Booking bookingID;

    @MapsId("vehicleID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "VehicleID", nullable = false)
    private Vehicle vehicleID;

    @Column(name = "AssignAt")
    private Instant assignAt;


}