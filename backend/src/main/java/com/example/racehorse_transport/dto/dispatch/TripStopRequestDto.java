package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.TripStopType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

// Một điểm dừng của lượt đón, giao, chạy thẳng, chuyển xe: điểm mạng lưới (locationId) hoặc địa chỉ của đơn (bookingAddressId)
public record TripStopRequestDto(
        @Positive Long locationId,
        @Positive Long bookingAddressId,
        @NotNull TripStopType stopType,
        @NotNull Instant plannedArrivalAt,
        @NotNull Instant plannedDepartureAt) {

    public TripStop toEntity(VehicleTrip trip, int sequenceNumber, Location location, BookingAddress bookingAddress) {
        TripStop stop = new TripStop();
        stop.setVehicleTrip(trip);
        stop.setSequenceNumber(sequenceNumber);
        stop.setLocation(location);
        stop.setBookingAddress(bookingAddress);
        stop.setStopType(stopType);
        stop.setPlannedArrivalAt(plannedArrivalAt);
        stop.setPlannedDepartureAt(plannedDepartureAt);
        return stop;
    }
}
