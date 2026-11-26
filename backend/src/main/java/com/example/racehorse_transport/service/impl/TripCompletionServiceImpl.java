package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.TripCompleteRequestDto;
import com.example.racehorse_transport.dto.operation.TripReturnPhotoRequestDto;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleEndCondition;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.HandoverService;
import com.example.racehorse_transport.service.StopReadinessService;
import com.example.racehorse_transport.service.TripCompletionService;
import com.example.racehorse_transport.service.TripReturnService;
import com.example.racehorse_transport.service.VehicleService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripCompletionServiceImpl implements TripCompletionService {

    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final CrewAssignmentRepository crew;
    private final HorseLegRepository legs;
    private final StopReadinessService readiness;
    private final DepartureService departures;
    private final HandoverService handovers;
    private final VehicleService vehicles;
    private final Clock clock;
    private final TripReturnService returns;

    @Override
    @Transactional
    public CrewTripResponseDto complete(Long tripId, TripCompleteRequestDto request, String username) {
        VehicleTrip trip = lockTrip(tripId);
        CrewAssignment member = requireCrew(tripId, username);
        TripExecutionPolicy.requireInProgress(trip.getStatus());
        TripStop last = lastStop(tripId);
        if (last.getStopType() == TripStopType.PICKUP || last.getStopType() == TripStopType.BORDER) {
            readiness.requireCanLeave(last, null, null);
        }
        TripExecutionPolicy.requireCanComplete(last.getActualArrivalAt() != null,
                legs.countByVehicleTrip_IdAndStatus(tripId, HorseLegStatus.IN_PROGRESS)
                        + legs.countFailedNotInHub(tripId, HorseLegStatus.FAILED, HandoverType.HUB_IN, HandoverStatus.DONE));
        returns.requirePhoto(tripId);
        TripCompleteRequestDto report = request == null ? new TripCompleteRequestDto(VehicleEndCondition.NORMAL, null) : request;
        TripExecutionPolicy.requireVehicleConditionNote(report.endVehicleCondition(), report.endVehicleNote());
        report.applyTo(trip);
        trip.setStatus(VehicleTripStatus.COMPLETED);
        trip.setActualEndAt(clock.instant());
        if (trip.getEndVehicleCondition() == VehicleEndCondition.NEEDS_MAINTENANCE) {
            var vehicle = vehicles.lockVehicle(trip.getVehicle().getId());
            vehicles.markForMaintenance(vehicle, member.getStaff().getUserId(), trip.getEndVehicleNote(), trip.getActualEndAt());
        }
        if (trip.getTripType() == VehicleTripType.LINEHAUL) {
            departures.linehaulCompleted(trip.getDeparture().getId());
        }
        return handovers.crewTrip(tripId, username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto attachReturnPhoto(Long tripId, TripReturnPhotoRequestDto request, String username) {
        VehicleTrip trip = lockTrip(tripId);
        CrewAssignment member = requireCrew(tripId, username);
        TripExecutionPolicy.requireInProgress(trip.getStatus());
        TripExecutionPolicy.requireLastStopArrived(lastStop(tripId).getActualArrivalAt() != null);
        returns.attachPhoto(tripId, request.file(), member.getStaff().getUserId());
        return handovers.crewTrip(tripId, username);
    }

    private VehicleTrip lockTrip(Long tripId) {
        return trips.findForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + tripId));
    }

    private CrewAssignment requireCrew(Long tripId, String username) {
        return crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(tripId, username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này."));
    }

    private TripStop lastStop(Long tripId) {
        var ordered = stops.findByVehicleTrip_IdOrderBySequenceNumber(tripId);
        TripExecutionPolicy.requireLastStopArrived(!ordered.isEmpty());
        return ordered.getLast();
    }
}
