package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.TripStopType;
import java.time.Instant;

public record TripStopResponseDto(
        Long id, Integer sequenceNumber, TripStopType stopType,
        Long locationId, String locationName,
        Long bookingAddressId, String bookingCode, String addressLine,
        Instant plannedArrivalAt, Instant plannedDepartureAt, Instant actualArrivalAt, Instant actualDepartureAt) {

    public static TripStopResponseDto from(TripStop stop) {
        var location = stop.getLocation();
        var address = stop.getBookingAddress();
        return new TripStopResponseDto(stop.getId(), stop.getSequenceNumber(), stop.getStopType(),
                location == null ? null : location.getId(), location == null ? null : location.getName(),
                address == null ? null : address.getId(), address == null ? null : address.getBooking().getCode(),
                address == null ? null : address.getAddressLine(),
                stop.getPlannedArrivalAt(), stop.getPlannedDepartureAt(), stop.getActualArrivalAt(), stop.getActualDepartureAt());
    }
}
