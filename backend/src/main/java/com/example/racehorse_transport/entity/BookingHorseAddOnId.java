package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

// Khóa của booking_horse_service.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class BookingHorseAddOnId implements Serializable {

    @Column(name = "booking_horse_id", nullable = false)
    private Long bookingHorseId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;
}
