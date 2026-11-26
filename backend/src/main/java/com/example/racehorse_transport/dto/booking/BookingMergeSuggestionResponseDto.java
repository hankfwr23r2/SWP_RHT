package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.enums.DepartureAvailability;
import com.example.racehorse_transport.entity.Departure;
import java.time.LocalDate;

public record BookingMergeSuggestionResponseDto(Long departureId, LocalDate departureDate, LocalDate requestedPickupDate,
                                                DepartureAvailability availability) {

    public static BookingMergeSuggestionResponseDto from(Departure target, LocalDate pickupDate, DepartureAvailability availability) {
        return new BookingMergeSuggestionResponseDto(target.getId(), target.getDepartureDate(), pickupDate, availability);
    }
}
