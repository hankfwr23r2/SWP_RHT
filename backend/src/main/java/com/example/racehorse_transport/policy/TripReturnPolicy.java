package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class TripReturnPolicy {
    public static final Progress NOT_RETURNING = new Progress(false, null, false);

    private TripReturnPolicy() {}

    public record Progress(boolean returning, Instant expectedAt, boolean late) {}

    public static Progress progress(VehicleTripStatus status, List<TripStop> ordered, boolean unfinishedLegs,
            Instant now, int lateMinutes) {
        if (status != VehicleTripStatus.IN_PROGRESS || ordered.isEmpty() || unfinishedLegs) {
            return NOT_RETURNING;
        }
        TripStop last = ordered.getLast();
        Duration delay = ordered.subList(0, ordered.size() - 1).reversed().stream()
                .filter(stop -> stop.getActualDepartureAt() != null)
                .findFirst()
                .map(stop -> Duration.between(stop.getPlannedDepartureAt(), stop.getActualDepartureAt()))
                .filter(value -> !value.isNegative()).orElse(Duration.ZERO);
        Instant expectedAt = last.getPlannedArrivalAt().plus(delay);
        Instant arrivedAt = last.getActualArrivalAt();
        Instant comparedAt = arrivedAt == null ? now : arrivedAt;
        return new Progress(arrivedAt == null, expectedAt, expectedAt.isBefore(lateCutoff(comparedAt, lateMinutes)));
    }

    public static Instant lateCutoff(Instant at, int lateMinutes) {
        return at.minus(Duration.ofMinutes(lateMinutes));
    }
}
