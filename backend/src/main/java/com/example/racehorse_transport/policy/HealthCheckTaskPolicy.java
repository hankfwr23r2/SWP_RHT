package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus;
import com.example.racehorse_transport.entity.enums.HealthCheckType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class HealthCheckTaskPolicy {
    public static final Set<HealthCheckTaskStatus> OPEN = Set.of(HealthCheckTaskStatus.ASSIGNED, HealthCheckTaskStatus.ACCEPTED);
    private static final Map<VehicleTripType, HealthCheckType> CHECKS_BY_TRIP = Map.of(
            VehicleTripType.LINEHAUL, HealthCheckType.BEFORE_DEPARTURE,
            VehicleTripType.DELIVERY, HealthCheckType.BEFORE_DELIVERY);
    public static final Set<HealthCheckType> HUB_EXIT_TYPES = Set.copyOf(CHECKS_BY_TRIP.values());

    private HealthCheckTaskPolicy() {}

    public static Set<VehicleTripType> hubExitTripTypes() {
        return CHECKS_BY_TRIP.keySet();
    }

    public static Optional<HealthCheckType> hubExitType(VehicleTripType tripType) {
        return Optional.ofNullable(CHECKS_BY_TRIP.get(tripType));
    }

    public static boolean isOpenAtHub(HorseStay stay) {
        return stay.getActualStartAt() != null && stay.getActualEndAt() == null
                && stay.getLocation().getLocationType() == LocationType.HUB;
    }

    public static boolean boardsFromHub(HorseLeg leg, HorseStay stay) {
        var trip = leg.getVehicleTrip();
        var stop = leg.getBoardTripStop();
        return leg.getStatus() == HorseLegStatus.PLANNED && trip != null
                && CHECKS_BY_TRIP.containsKey(trip.getTripType())
                && trip.getStatus() != VehicleTripStatus.CANCELLED && trip.getStatus() != VehicleTripStatus.COMPLETED
                && stop != null && stop.getStopType() == TripStopType.HUB && stop.getActualDepartureAt() == null
                && stop.getLocation() != null && stop.getLocation().getId().equals(stay.getLocation().getId())
                && leg.getBookingHorse().getId().equals(stay.getBookingHorse().getId()) && isOpenAtHub(stay);
    }

    public static boolean arrivedAtHub(HorseLeg leg, HorseStay stay) {
        return leg.getStatus() == HorseLegStatus.COMPLETED && leg.getAlightTripStop() != null
                && leg.getAlightTripStop().getLocation() != null
                && leg.getAlightTripStop().getLocation().getId().equals(stay.getLocation().getId());
    }

    public static boolean overdue(HealthCheckTaskStatus status, Instant dueAt, Instant now) {
        return OPEN.contains(status) && dueAt.isBefore(now);
    }

    public static void requireTransition(HealthCheckTaskStatus status, HealthCheckTaskStatus target) {
        boolean allowed = switch (target) {
            case ACCEPTED -> status == HealthCheckTaskStatus.ASSIGNED;
            case DONE, CANCELLED -> OPEN.contains(status);
            case ASSIGNED -> false;
        };
        if (!allowed) throw new DispatchRuleException("status", "Việc khám đã đổi trạng thái, vui lòng tải lại.", true);
    }

    public static void requireFutureDue(Instant dueAt, Instant now) {
        if (!dueAt.isAfter(now)) throw new DispatchRuleException("dueAt", "Hạn khám phải ở tương lai.", false);
    }

    public static void requireReassignable(HealthCheckTaskStatus status) {
        if (!OPEN.contains(status)) {
            throw new DispatchRuleException("status", "Việc khám đã hoàn tất.", true);
        }
    }
}
