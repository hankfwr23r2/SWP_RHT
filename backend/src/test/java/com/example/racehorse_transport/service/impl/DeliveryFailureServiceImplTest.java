package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeliveryFailureServiceImplTest {
    private final Instant now = Instant.parse("2026-11-25T04:00:00Z");
    private final HandoverRepository handovers = mock(HandoverRepository.class);
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final CrewAssignmentRepository crew = mock(CrewAssignmentRepository.class);
    private final TripStopRepository stops = mock(TripStopRepository.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final DeliveryFailureServiceImpl service = new DeliveryFailureServiceImpl(handovers, legs, crew,
            mock(IncidentRepository.class), mock(StaffRepository.class), mock(NotificationService.class), configs,
            Clock.fixed(now, ZoneOffset.UTC), stops);

    @Test
    void timesOutOnlyTheIntermediateStopAfterOneHour() {
        var trip = new VehicleTrip();
        trip.setId(1L);
        trip.setStatus(VehicleTripStatus.IN_PROGRESS);
        var first = stop(trip, 11L, 2, TripStopType.DELIVERY);
        var last = stop(trip, 12L, 3, TripStopType.DELIVERY);
        var home = stop(trip, 13L, 4, TripStopType.HUB);
        var firstHandover = handover(first);
        var lastHandover = handover(last);
        when(configs.getInt("INTERMEDIATE_WAIT_MINUTES")).thenReturn(60);
        when(configs.getInt("HANDOVER_FREE_WAIT_MINUTES")).thenReturn(120);
        when(stops.findByVehicleTrip_IdOrderBySequenceNumber(1L)).thenReturn(List.of(first, last, home));
        when(handovers.findWaitingSince(HandoverStatus.PENDING, now.minusSeconds(3600)))
                .thenReturn(List.of(firstHandover, lastHandover));

        assertThat(service.timeoutExpired()).isEqualTo(1);

        assertThat(firstHandover.getStatus()).isEqualTo(HandoverStatus.TIMEOUT);
        assertThat(lastHandover.getStatus()).isEqualTo(HandoverStatus.PENDING);
        verify(legs).findByAlightTripStop_IdAndStatusIn(11L, Set.of(HorseLegStatus.IN_PROGRESS));
        org.mockito.Mockito.verifyNoMoreInteractions(legs);
    }

    @Test
    void rejectsManualFailureWhileTheLastRecipientCanStillArrive() {
        var trip = new VehicleTrip();
        trip.setId(1L);
        trip.setStatus(VehicleTripStatus.IN_PROGRESS);
        var last = stop(trip, 12L, 3, TripStopType.DELIVERY);
        var finalHandover = handover(last);
        when(stops.findByVehicleTrip_IdOrderBySequenceNumber(1L)).thenReturn(List.of(last));
        when(configs.getInt("HANDOVER_FREE_WAIT_MINUTES")).thenReturn(120);
        when(handovers.findForUpdate(1L)).thenReturn(Optional.of(finalHandover));
        when(crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(1L, "groom", CrewAssignmentStatus.RELEASED))
                .thenReturn(Optional.of(new com.example.racehorse_transport.entity.CrewAssignment()));

        assertThatThrownBy(() -> service.fail(1L, "groom")).isInstanceOf(DispatchRuleException.class);
        assertThat(finalHandover.getStatus()).isEqualTo(HandoverStatus.PENDING);
    }

    private Handover handover(TripStop stop) {
        var handover = new Handover();
        handover.setTripStop(stop);
        handover.setStatus(HandoverStatus.PENDING);
        handover.setWaitStartedAt(now.minusSeconds(3600));
        return handover;
    }

    private static TripStop stop(VehicleTrip trip, Long id, int number, TripStopType type) {
        var stop = new TripStop();
        stop.setId(id);
        stop.setVehicleTrip(trip);
        stop.setSequenceNumber(number);
        stop.setStopType(type);
        return stop;
    }
}
