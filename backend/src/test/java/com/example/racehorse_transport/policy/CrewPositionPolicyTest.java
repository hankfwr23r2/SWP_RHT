package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrewPositionPolicyTest {
    private static final Instant AT = Instant.parse("2031-03-05T08:00:00Z");
    private final Location home = new Location();
    private final Location other = new Location();
    private final Location last = new Location();

    @Test
    void usesActualCompletionAndProjectsLaterPlannedArrival() {
        var completed = assignment(1, VehicleTripStatus.COMPLETED, other);
        completed.getVehicleTrip().setActualEndAt(AT.minusSeconds(30));
        completed.getVehicleTrip().setPlannedEndAt(AT.plusSeconds(30));
        var planned = assignment(2, VehicleTripStatus.PLANNED, last);
        planned.getVehicleTrip().setPlannedEndAt(AT.plusSeconds(60));

        assertThat(CrewPositionPolicy.at(home, List.of(completed, planned), null, AT, AT).location()).isSameAs(other);
        assertThat(CrewPositionPolicy.at(home, List.of(completed, planned), null, AT.plusSeconds(60), AT).location()).isSameAs(last);
    }

    @Test
    void reportsRunningTripAndIgnoresReleasedAndExcludedAssignments() {
        var running = assignment(1, VehicleTripStatus.IN_PROGRESS, other);
        running.getVehicleTrip().setActualStartAt(AT.minusSeconds(3600));
        running.getVehicleTrip().setPlannedEndAt(AT.minusSeconds(5));
        var released = assignment(2, VehicleTripStatus.PLANNED, last);
        released.getVehicleTrip().setPlannedEndAt(AT.minusSeconds(10));
        released.setStatus(CrewAssignmentStatus.RELEASED);

        assertThat(CrewPositionPolicy.at(home, List.of(running, released), null, AT, AT))
                .isEqualTo(new CrewPositionPolicy.Position(null, "VT-1"));
        assertThat(CrewPositionPolicy.at(home, List.of(running, released), 1L, AT, AT).location()).isSameAs(home);
    }

    @Test
    void projectsARunningTripToItsEndForALaterStart() {
        var running = assignment(1, VehicleTripStatus.IN_PROGRESS, other);
        running.getVehicleTrip().setActualStartAt(AT.minusSeconds(3600));
        running.getVehicleTrip().setPlannedEndAt(AT.plusSeconds(600));

        assertThat(CrewPositionPolicy.at(home, List.of(running), null, AT.plusSeconds(900), AT).location()).isSameAs(other);
        assertThat(CrewPositionPolicy.at(home, List.of(running), null, AT.plusSeconds(300), AT).tripCode()).isEqualTo("VT-1");
    }

    private static CrewAssignment assignment(long id, VehicleTripStatus status, Location end) {
        VehicleTrip trip = new VehicleTrip();
        trip.setId(id);
        trip.setCode("VT-" + id);
        trip.setStatus(status);
        trip.setEndLocation(end);
        CrewAssignment assignment = new CrewAssignment();
        assignment.setVehicleTrip(trip);
        assignment.setStatus(CrewAssignmentStatus.ASSIGNED);
        return assignment;
    }
}
