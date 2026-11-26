package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.impl.DeparturePlanShiftServiceImpl;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeparturePlanShiftServiceTest {
    private static final Instant T = Instant.parse("2027-01-15T03:00:00Z");
    private final VehicleTripRepository trips = mock(VehicleTripRepository.class);
    private final TripStopRepository stops = mock(TripStopRepository.class);
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final DeparturePlanShiftService service = new DeparturePlanShiftServiceImpl(trips, stops, legs,
            mock(VehicleRepository.class), mock(CrewAssignmentRepository.class), mock(StaffRepository.class), mock(SystemConfigService.class));

    @Test
    void shiftsTripStopAndHorseLegPlanTogether() {
        var trip = trip();
        var stop = stop(trip);
        var leg = new HorseLeg();
        leg.setVehicleTrip(trip);
        leg.setStatus(HorseLegStatus.PLANNED);
        leg.setPlannedStartAt(T);
        leg.setPlannedEndAt(T.plusSeconds(3600));
        when(legs.findByVehicleTrip_IdIn(List.of(5L))).thenReturn(List.of(leg));
        service.shift(15L, service.lockAffectedTrips(15L), Duration.ofHours(2));
        assertThat(trip.getPlannedStartAt()).isEqualTo(T.plusSeconds(7200));
        assertThat(stop.getPlannedDepartureAt()).isEqualTo(T.plusSeconds(7200));
        assertThat(leg.getPlannedEndAt()).isEqualTo(T.plusSeconds(10800));
        assertThat(leg.getActualStartAt()).isNull();
    }

    @Test
    void rejectsAStartedTripAndATripThatWasAddedAfterLocking() {
        var trip = trip();
        stop(trip).setActualDepartureAt(T);
        assertThatThrownBy(() -> service.shift(15L, Set.of(5L), Duration.ofHours(2))).isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> service.shift(15L, Set.of(), Duration.ofHours(2))).isInstanceOf(BookingRuleException.class);
        assertThat(trip.getPlannedStartAt()).isEqualTo(T);
    }

    private VehicleTrip trip() {
        var hub = new Location();
        hub.setId(40L);
        hub.setName("Hub thử");
        var vehicle = new Vehicle();
        vehicle.setId(8L);
        vehicle.setHomeHubLocation(hub);
        var trip = new VehicleTrip();
        trip.setId(5L);
        trip.setVehicle(vehicle);
        trip.setStartLocation(hub);
        trip.setTripType(VehicleTripType.LINEHAUL);
        trip.setPlannedStartAt(T);
        trip.setPlannedEndAt(T.plusSeconds(3600));
        when(trips.findIdsForDelayShift(15L)).thenReturn(List.of(5L));
        when(trips.findForUpdate(5L)).thenReturn(Optional.of(trip));
        when(trips.findByVehicle_IdAndStatusNot(8L, VehicleTripStatus.CANCELLED)).thenReturn(List.of(trip));
        return trip;
    }

    private TripStop stop(VehicleTrip trip) {
        var stop = new TripStop();
        stop.setVehicleTrip(trip);
        stop.setPlannedArrivalAt(T);
        stop.setPlannedDepartureAt(T);
        when(stops.findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(List.of(5L))).thenReturn(List.of(stop));
        return stop;
    }
}
