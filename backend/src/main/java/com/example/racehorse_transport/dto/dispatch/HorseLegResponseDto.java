package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import java.time.Instant;

// Một chặng của con ngựa. fromName, toName: tên điểm mạng lưới hoặc dòng địa chỉ của đơn
public record HorseLegResponseDto(
        Long id, Long bookingHorseId, Integer sequenceNumber, HorseLegType legType, HorseLegStatus status,
        Long vehicleTripId, String vehicleTripCode, Long boardTripStopId, Long alightTripStopId,
        String fromName, String toName, Instant plannedStartAt, Instant plannedEndAt) {

    public static HorseLegResponseDto from(HorseLeg leg) {
        var trip = leg.getVehicleTrip();
        return new HorseLegResponseDto(leg.getId(), leg.getBookingHorse().getId(), leg.getSequenceNumber(), leg.getLegType(), leg.getStatus(),
                trip == null ? null : trip.getId(), trip == null ? null : trip.getCode(),
                leg.getBoardTripStop() == null ? null : leg.getBoardTripStop().getId(),
                leg.getAlightTripStop() == null ? null : leg.getAlightTripStop().getId(),
                nameOf(leg.getFromLocation(), leg.getFromBookingAddress()), nameOf(leg.getToLocation(), leg.getToBookingAddress()),
                leg.getPlannedStartAt(), leg.getPlannedEndAt());
    }

    private static String nameOf(Location location, BookingAddress address) {
        return location != null ? location.getName() : address.getAddressLine();
    }
}
