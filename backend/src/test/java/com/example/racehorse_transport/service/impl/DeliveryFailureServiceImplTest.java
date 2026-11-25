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
import com.example.racehorse_transport.policy.DeliveryEtaPolicy;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
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
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final DeliveryFailureServiceImpl service = new DeliveryFailureServiceImpl(handovers, legs, crew,
            incidents, mock(StaffRepository.class), mock(NotificationService.class), configs,
            Clock.fixed(now, ZoneOffset.UTC), stops);

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void lateTruckMakesTheCompanyResponsibleUnlessTheNewTimeWasAnnounced(boolean announced) {
        var trip = new VehicleTrip();
        trip.setId(1L);
        trip.setStatus(VehicleTripStatus.IN_PROGRESS);
        var stop = stop(trip, 12L, 3, TripStopType.DELIVERY);
        stop.setPlannedArrivalAt(now.minusSeconds(9000));
        stop.setActualArrivalAt(now.minusSeconds(3600));
        if (announced) {
            stop.setToldArrivalAt(stop.getActualArrivalAt().minusSeconds(1800));
            stop.setToldAt(stop.getPlannedArrivalAt().minusSeconds(3600));
        }
        var waiting = handover(stop);
        waiting.setWaitStartedAt(now.minusSeconds(7200));
        var horse = new com.example.racehorse_transport.entity.Horse();
        horse.setName("Sao");
        var line = new com.example.racehorse_transport.entity.BookingHorse();
        line.setHorse(horse);
        line.setBooking(stop.getBookingAddress().getBooking());
        var leg = new com.example.racehorse_transport.entity.HorseLeg();
        leg.setBookingHorse(line);
        when(configs.getInt("INTERMEDIATE_WAIT_MINUTES")).thenReturn(60);
        when(configs.getInt("HANDOVER_FREE_WAIT_MINUTES")).thenReturn(120);
        when(configs.getInt(DeliveryEtaPolicy.TOLERANCE)).thenReturn(30);
        when(stops.findByVehicleTrip_IdOrderBySequenceNumber(1L)).thenReturn(List.of(stop));
        when(handovers.findWaitingSince(HandoverStatus.PENDING, now.minusSeconds(3600))).thenReturn(List.of(waiting));
        when(legs.findByAlightTripStop_IdAndStatusIn(12L, Set.of(HorseLegStatus.IN_PROGRESS))).thenReturn(List.of(leg));

        assertThat(service.timeoutExpired()).isEqualTo(1);
        verify(incidents).save(org.mockito.ArgumentMatchers.argThat(row -> row.getConfirmedCauseType()
                == (announced ? IncidentCauseType.RECIPIENT_ABSENT : IncidentCauseType.COMPANY_FAULT)));
    }

    @Test
    void waitingStartsAtOpeningOfTheAnnouncedWindowWhenTheTruckArrivesEarly() {
        var trip = new VehicleTrip();
        trip.setId(1L);
        trip.setStatus(VehicleTripStatus.IN_PROGRESS);
        var stop = stop(trip, 12L, 3, TripStopType.DELIVERY);
        stop.setToldArrivalAt(now.plusSeconds(3600));
        var handover = handover(stop);
        handover.setHandoverType(com.example.racehorse_transport.entity.enums.HandoverType.DELIVERY);
        handover.setWaitStartedAt(null);
        when(handovers.findForUpdate(1L)).thenReturn(Optional.of(handover));
        when(crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(1L, "groom", CrewAssignmentStatus.RELEASED))
                .thenReturn(Optional.of(new com.example.racehorse_transport.entity.CrewAssignment()));
        when(configs.getInt(DeliveryEtaPolicy.TOLERANCE)).thenReturn(30);

        service.startWaiting(1L, "groom");

        assertThat(handover.getWaitStartedAt()).isEqualTo(now.plusSeconds(1800));
    }

    @Test
    void stopsOfDifferentBookingsKeepTheFullWait() {
        var trip = new VehicleTrip();
        trip.setId(1L);
        trip.setStatus(VehicleTripStatus.IN_PROGRESS);
        var first = stop(trip, 11L, 2, TripStopType.DELIVERY);
        var other = stop(trip, 12L, 3, TripStopType.DELIVERY);
        other.getBookingAddress().getBooking().setId(8L);
        when(configs.getInt("INTERMEDIATE_WAIT_MINUTES")).thenReturn(60);
        when(configs.getInt("HANDOVER_FREE_WAIT_MINUTES")).thenReturn(120);
        when(stops.findByVehicleTrip_IdOrderBySequenceNumber(1L)).thenReturn(List.of(first, other));
        when(handovers.findWaitingSince(HandoverStatus.PENDING, now.minusSeconds(3600))).thenReturn(List.of(handover(first)));

        assertThat(service.timeoutExpired()).isZero();
    }

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
        if (type == TripStopType.DELIVERY) {
            var booking = new com.example.racehorse_transport.entity.Booking();
            booking.setId(7L);
            var address = new com.example.racehorse_transport.entity.BookingAddress();
            address.setBooking(booking);
            stop.setBookingAddress(address);
        }
        return stop;
    }
}
