package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

public final class CrewPositionPolicy {

    private CrewPositionPolicy() {}

    public record Position(Location location, String tripCode) {}

    public static Position at(Location homeHub, Collection<CrewAssignment> assignments, Long excludedTripId, Instant at, Instant now) {
        var trips = assignments.stream()
                .filter(a -> a.getStatus() != CrewAssignmentStatus.RELEASED)
                .map(CrewAssignment::getVehicleTrip)
                .filter(t -> excludedTripId == null || !Objects.equals(excludedTripId, t.getId()))
                .toList();
        var running = trips.stream()
                .filter(t -> t.getStatus() == VehicleTripStatus.IN_PROGRESS)
                // Xét giờ sau lúc lượt đang chạy dự kiến xong thì coi như đã tới điểm cuối
                .filter(t -> t.getActualStartAt() != null && !t.getActualStartAt().isAfter(at))
                .filter(t -> !at.isAfter(now) || t.getPlannedEndAt() == null || t.getPlannedEndAt().isAfter(at))
                .max(Comparator.comparing(VehicleTrip::getActualStartAt).thenComparing(VehicleTrip::getId));
        if (running.isPresent()) {
            return new Position(null, running.get().getCode());
        }
        Location location = trips.stream()
                .filter(t -> arrival(t) != null && !arrival(t).isAfter(at))
                .max(Comparator.comparing(CrewPositionPolicy::arrival).thenComparing(VehicleTrip::getId))
                .map(VehicleTrip::getEndLocation)
                .orElse(homeHub);
        return new Position(location, null);
    }

    private static Instant arrival(VehicleTrip trip) {
        return switch (trip.getStatus()) {
            case COMPLETED -> trip.getActualEndAt();
            case PLANNED, DISPATCHED, IN_PROGRESS -> trip.getPlannedEndAt();
            default -> null;
        };
    }
}
