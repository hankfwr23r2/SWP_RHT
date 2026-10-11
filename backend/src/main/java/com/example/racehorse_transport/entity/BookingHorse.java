package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "BOOKING_HORSE")
public class BookingHorse {
    @EmbeddedId
    private BookingHorseId id;

    @MapsId("bookingID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "BookingID", nullable = false)
    private Booking bookingID;

    @MapsId("horseID")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "HorseID", nullable = false)
    private Horse horseID;

    @Column(name = "AddedDate")
    private Instant addedDate;


}