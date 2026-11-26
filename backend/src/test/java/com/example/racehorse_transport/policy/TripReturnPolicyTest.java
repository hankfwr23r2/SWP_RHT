package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripReturnPolicyTest {
    private static final Instant PLANNED_RETURN = Instant.parse("2026-12-01T12:00:00Z");

    @Test
    void addsOnlyTheDelayFromTheMostRecentDeparture() {
        TripStop first = stop("08:00:00", "08:40:00");
        TripStop later = stop("09:00:00", "09:20:00");
        TripStop notYetLeft = stop("10:00:00", null);
        assertThat(progress(List.of(first, later, notYetLeft, last()), PLANNED_RETURN).expectedAt())
                .isEqualTo(PLANNED_RETURN.plusSeconds(20 * 60));

        later.setActualDepartureAt(later.getPlannedDepartureAt().minusSeconds(60));
        assertThat(progress(List.of(first, later, last()), PLANNED_RETURN).expectedAt()).isEqualTo(PLANNED_RETURN);
        assertThat(progress(List.of(notYetLeft, last()), PLANNED_RETURN).expectedAt()).isEqualTo(PLANNED_RETURN);
    }

    @Test
    void isLateOnlyAfterTheConfiguredGracePeriod() {
        var ordered = List.of(stop("09:00:00", "09:20:00"), last());
        Instant cutoff = PLANNED_RETURN.plusSeconds((20 + 30) * 60);
        assertThat(progress(ordered, cutoff)).isEqualTo(new TripReturnPolicy.Progress(true, PLANNED_RETURN.plusSeconds(20 * 60), false));
        assertThat(progress(ordered, cutoff.plusSeconds(1)).late()).isTrue();
    }

    @Test
    void onlyAnEmptyRunningTripBeforeTheLastStopIsReturning() {
        TripStop last = last();
        var ordered = List.of(last);
        assertThat(TripReturnPolicy.progress(VehicleTripStatus.IN_PROGRESS, ordered, true, PLANNED_RETURN, 30))
                .isEqualTo(TripReturnPolicy.NOT_RETURNING);
        assertThat(TripReturnPolicy.progress(VehicleTripStatus.COMPLETED, ordered, false, PLANNED_RETURN, 30))
                .isEqualTo(TripReturnPolicy.NOT_RETURNING);
        last.setActualArrivalAt(PLANNED_RETURN.plusSeconds(30 * 60 + 1));
        assertThat(progress(ordered, last.getActualArrivalAt()))
                .isEqualTo(new TripReturnPolicy.Progress(false, PLANNED_RETURN, true));
    }

    private static TripReturnPolicy.Progress progress(List<TripStop> ordered, Instant now) {
        return TripReturnPolicy.progress(VehicleTripStatus.IN_PROGRESS, ordered, false, now, 30);
    }

    private static TripStop last() {
        var stop = new TripStop();
        stop.setPlannedArrivalAt(PLANNED_RETURN);
        return stop;
    }

    private static TripStop stop(String planned, String actual) {
        var stop = new TripStop();
        stop.setPlannedDepartureAt(Instant.parse("2026-12-01T" + planned + "Z"));
        if (actual != null) stop.setActualDepartureAt(Instant.parse("2026-12-01T" + actual + "Z"));
        return stop;
    }
}
