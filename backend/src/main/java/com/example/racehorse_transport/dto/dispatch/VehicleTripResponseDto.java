package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.VehicleEndCondition;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.policy.TripReturnPolicy;
import com.example.racehorse_transport.policy.TripReturnPolicy.Progress;
import java.time.Instant;
import java.util.List;

// vehicleSeats: tổng chỗ của xe (khoang thường 1, khoang rộng 2)
public record VehicleTripResponseDto(
        Long id, String code, VehicleTripType tripType, VehicleTripStatus status,
        Long departureId, String routeCode,
        Long vehicleId, String licensePlate, Integer vehicleSeats,
        Long startLocationId, String startLocationName, Long endLocationId, String endLocationName,
        Instant plannedStartAt, Instant plannedEndAt, Instant actualStartAt, Instant actualEndAt,
        VehicleEndCondition endVehicleCondition, String endVehicleNote,
        List<TripStopResponseDto> stops, List<CrewAssignmentResponseDto> crew, Long coordinatorId, String coordinatorName,
        long openIncidentCount, String coordinatorStaffCode, boolean takeOverAllowed, boolean manualStopTimesEditable,
        boolean returning, Instant returnExpectedAt, boolean returnLate, boolean returnPhotoAttached) {

    public static VehicleTripResponseDto from(VehicleTrip trip, List<TripStopResponseDto> stops, List<CrewAssignmentResponseDto> crew) {
        return from(trip, stops, crew, 0, false, false, TripReturnPolicy.NOT_RETURNING, false);
    }

    public static VehicleTripResponseDto from(VehicleTrip trip, List<TripStopResponseDto> stops,
            List<CrewAssignmentResponseDto> crew, long openIncidentCount, boolean takeOverAllowed, boolean manualStopTimesEditable,
            Progress returnProgress, boolean returnPhotoAttached) {
        var vehicle = trip.getVehicle();
        var departure = trip.getDeparture();
        return new VehicleTripResponseDto(trip.getId(), trip.getCode(), trip.getTripType(), trip.getStatus(),
                departure == null ? null : departure.getId(), departure == null ? null : departure.getRoute().getCode(),
                vehicle.getId(), vehicle.getLicensePlate(), seatsOf(vehicle.getStandardStallCount(), vehicle.getWideStallCount()),
                trip.getStartLocation().getId(), trip.getStartLocation().getName(),
                trip.getEndLocation().getId(), trip.getEndLocation().getName(),
                trip.getPlannedStartAt(), trip.getPlannedEndAt(), trip.getActualStartAt(), trip.getActualEndAt(),
                trip.getEndVehicleCondition(), trip.getEndVehicleNote(),
                stops, crew,
                trip.getCoordinator() == null ? null : trip.getCoordinator().getUserId(),
                trip.getCoordinator() == null ? null : trip.getCoordinator().getFullName(), openIncidentCount,
                trip.getCoordinator() == null ? null : trip.getCoordinator().getStaffCode(), takeOverAllowed, manualStopTimesEditable,
                returnProgress.returning(), returnProgress.expectedAt(), returnProgress.late(), returnPhotoAttached);
    }

    static int seatsOf(int standardStallCount, int wideStallCount) {
        return standardStallCount * StallSeatPolicy.seats("STANDARD") + wideStallCount * StallSeatPolicy.seats("WIDE");
    }
}
