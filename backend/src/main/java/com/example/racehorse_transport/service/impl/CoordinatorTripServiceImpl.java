package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.*;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.CareCheckPolicy;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import java.util.Set;
import com.example.racehorse_transport.service.*;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoordinatorTripServiceImpl implements CoordinatorTripService {
    private final CoordinatorAssignmentService assignments;
    private final DispatchService dispatch;
    private final HorseLegRepository legs;
    private final HorseHealthLogRepository healthLogs;
    private final DocumentCompletenessService documents;
    private final SystemConfigService configs;
    private final Clock clock;

    @Override
    public List<CoordinatorTripResponseDto> mine(String username) {
        var now = clock.instant();
        return dispatch.ownedTrips(assignments.currentStaff(username).getUserId()).stream().map(trip -> {
            var current = trip.stops().stream().filter(stop -> stop.actualArrivalAt() != null).reduce((first, last) -> last);
            String point = current.map(stop -> stop.locationName() == null ? stop.addressLine() : stop.locationName())
                    .orElse(trip.startLocationName());
            boolean late = (trip.actualStartAt() == null && trip.plannedStartAt().isBefore(now)) || trip.plannedEndAt().isBefore(now);
            return new CoordinatorTripResponseDto(trip, point, late, legs.findActiveOnTrip(trip.id(), Set.of(HorseLegStatus.CANCELLED)).size());
        }).toList();
    }

    @Override
    public List<CoordinatorTripHorseResponseDto> horses(Long tripId, String username) {
        VehicleTripResponseDto trip = dispatch.trip(tripId);
        if (!Objects.equals(trip.coordinatorId(), assignments.currentStaff(username).getUserId())) {
            throw BookingRuleException.forbidden("Lượt xe do " + trip.coordinatorName() + " phụ trách.");
        }
        int maxHours = configs.getInt("MAX_CONTINUOUS_HOURS");
        return legs.findActiveOnTrip(tripId, Set.of(HorseLegStatus.CANCELLED)).stream().map(leg -> {
            var last = healthLogs.findFirstByBookingHorse_IdOrderByRecordedAtDesc(leg.getBookingHorse().getId())
                    .map(log -> log.getRecordedAt()).orElse(null);
            boolean boarded = leg.getStatus() == HorseLegStatus.IN_PROGRESS;
            var next = boarded && leg.getActualStartAt() != null
                    ? CareCheckPolicy.nextCheckAt(leg.getActualStartAt(), last, true, maxHours) : null;
            return new CoordinatorTripHorseResponseDto(TripHorseResponseDto.from(leg), boarded,
                    documents.isComplete(leg.getBookingHorse().getBooking().getId()), last, next);
        }).toList();
    }
}
