package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.dto.operation.PickupMissChoiceRequestDto;
import com.example.racehorse_transport.dto.operation.PickupMissLiabilityRequestDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.impl.PickupMissServiceImpl;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PickupMissServiceTest {
    private static final Instant NOW = Instant.parse("2026-12-07T00:00:00Z");
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingHorseRepository horses = mock(BookingHorseRepository.class);
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final BookingHorseService bookingHorses = mock(BookingHorseService.class);
    private final BookingCancellationService cancellations = mock(BookingCancellationService.class);
    private final PickupMissEvidenceService evidence = mock(PickupMissEvidenceService.class);
    private final HorseStayService stays = mock(HorseStayService.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final PickupMissService service = new PickupMissServiceImpl(bookings, horses, legs, users, bookingHorses,
            cancellations, evidence, stays, configs, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Booking booking = new Booking();
    private final BookingHorse horse = new BookingHorse();

    @BeforeEach
    void setUp() {
        AppUser owner = new AppUser();
        owner.setId(7L);
        owner.setUsername("owner");
        Customer customer = new Customer();
        customer.setUser(owner);
        booking.setId(2L);
        booking.setCustomer(customer);
        booking.setBookingType(BookingType.LOCAL);
        booking.setStatus(BookingStatus.CONFIRMED);
        horse.setId(3L);
        horse.setHorse(new Horse());
        horse.setBooking(booking);
        horse.setStatus(BookingHorseStatus.ACTIVE);
        when(bookings.findForUpdate(2L)).thenReturn(Optional.of(booking));
        when(horses.findBookingId(3L)).thenReturn(Optional.of(2L));
        when(horses.findById(3L)).thenReturn(Optional.of(horse));
        when(horses.findByBooking_Id(2L)).thenReturn(List.of(horse));
        when(users.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(configs.getInt("PICKUP_CHOICE_LONG_HOURS")).thenReturn(12);
        when(configs.getInt("PICKUP_CHOICE_SHORT_HOURS")).thenReturn(1);
        when(configs.getInt("HUB_CHECKIN_HOURS")).thenReturn(3);
        when(configs.getInt("PRICE_HOLD_DAYS")).thenReturn(30);
    }

    @Test
    void recordingTwiceKeepsTheHorseAndRecordsEvidenceOnce() {
        HorseLeg planned = new HorseLeg();
        planned.setStatus(HorseLegStatus.PLANNED);
        when(legs.findByBookingHorse_IdOrderBySequenceNumber(3L)).thenReturn(List.of(planned));

        service.record(3L, "Chip mismatch", NOW, 4L);
        service.record(3L, "Repeated", NOW, 4L);

        assertThat(horse.getPickupMissReason()).isEqualTo("Chip mismatch");
        assertThat(horse.getStatus()).isEqualTo(BookingHorseStatus.ACTIVE);
        assertThat(planned.getStatus()).isEqualTo(HorseLegStatus.CANCELLED);
        verify(evidence).record(horse, null, null, null, NOW, 4L);
        verifyNoInteractions(bookingHorses, cancellations);
    }

    @Test
    void singleMissCanChooseAnotherDirectPickupDateAndRetryTheRequest() {
        service.record(3L, "Chip mismatch", NOW, 4L);
        var date = LocalDate.of(2026, 12, 8);
        var request = new PickupMissChoiceRequestDto(PickupMissChoice.RESCHEDULE_PICKUP, null, null, date);
        assertThat(service.decide(3L, request, "owner").choice()).isEqualTo(PickupMissChoice.RESCHEDULE_PICKUP);
        assertThat(service.decide(3L, request, "owner").choice()).isEqualTo(PickupMissChoice.RESCHEDULE_PICKUP);
        assertThat(booking.getRequestedPickupDate()).isEqualTo(date);
        verify(evidence).audit(eq(7L), eq("CHOOSE_AFTER_PICKUP_MISS"), eq(3L), anyMap());
    }

    @Test
    void dIsRejectedAfterAnotherHorseWasReceived() {
        trunk();
        service.record(3L, "Did not board", NOW, 4L);
        when(bookings.hasReceivedHorses(2L)).thenReturn(true);
        var request = new PickupMissChoiceRequestDto(PickupMissChoice.CHANGE_DEPARTURE, null, null, null);
        assertThatThrownBy(() -> service.decide(3L, request, "owner")).isInstanceOf(BookingRuleException.class);
        verifyNoInteractions(cancellations);
    }

    @Test
    void deadlineReleasesAnEntireMissedBookingOnlyOnce() {
        trunk();
        service.record(3L, "Did not board", NOW.minus(Duration.ofHours(13)), 4L);
        service.expire(3L);
        service.expire(3L);
        verify(cancellations).releaseAfterPickupMiss(2L, false, null);
        assertThat(horse.getPickupMissChoice()).isEqualTo(PickupMissChoice.CHANGE_DEPARTURE);
    }

    @Test
    void cSchedulesAnAppointmentAndRejectsLateArrival() {
        trunk();
        service.record(3L, "Did not board", NOW, 4L);
        var late = new PickupMissChoiceRequestDto(PickupMissChoice.SELF_DELIVERY, null, NOW.plus(Duration.ofHours(48)), null);
        assertThatThrownBy(() -> service.decide(3L, late, "owner")).isInstanceOf(BookingRuleException.class);
        Instant arrival = NOW.plus(Duration.ofHours(4));
        var preparedRetry = new HorseLeg();
        preparedRetry.setLegType(HorseLegType.PICKUP);
        preparedRetry.setStatus(HorseLegStatus.PLANNED);
        when(legs.findByBookingHorse_IdOrderBySequenceNumber(3L)).thenReturn(List.of(preparedRetry));
        var request = new PickupMissChoiceRequestDto(PickupMissChoice.SELF_DELIVERY, null, arrival, null);
        service.decide(3L, request, "owner");
        service.decide(3L, request, "owner");
        verify(stays).planSelfDropOff(3L, arrival);
        assertThat(preparedRetry.getStatus()).isEqualTo(HorseLegStatus.CANCELLED);
    }

    @Test
    void anotherHorseAwaitingPickupPreventsReleasingTheWholeBooking() {
        trunk();
        var other = new BookingHorse();
        other.setId(4L);
        other.setBooking(booking);
        other.setStatus(BookingHorseStatus.ACTIVE);
        when(horses.findByBooking_Id(2L)).thenReturn(List.of(horse, other));
        service.record(3L, "Did not board", NOW.minus(Duration.ofHours(13)), 4L);

        service.expire(3L);

        verifyNoInteractions(cancellations);
        assertThat(horse.getPickupMissChoice()).isNull();

        when(bookings.hasReceivedHorses(2L)).thenReturn(true);
        service.expire(3L);
        service.expire(3L);

        verify(bookingHorses).exclude(3L, "Did not board", 7L);
        assertThat(horse.getPickupMissChoice()).isEqualTo(PickupMissChoice.DROP_HORSE);
    }

    @Test
    void coordinatorMustRecordTheCustomersPhoneDecision() {
        var coordinator = new AppUser();
        coordinator.setId(9L);
        var role = new Role();
        role.setCode("COORDINATOR");
        coordinator.setRole(role);
        when(users.findByUsername("coordinator")).thenReturn(Optional.of(coordinator));
        service.record(3L, "Did not board", NOW, 4L);
        var date = LocalDate.of(2026, 12, 8);
        var missingNote = new PickupMissChoiceRequestDto(PickupMissChoice.RESCHEDULE_PICKUP, null, null, date);
        assertThatThrownBy(() -> service.decide(3L, missingNote, "coordinator"))
                .isInstanceOf(BookingRuleException.class);
        var confirmed = new PickupMissChoiceRequestDto(PickupMissChoice.RESCHEDULE_PICKUP, "Phone confirmation", null, date);
        service.decide(3L, confirmed, "coordinator");
        service.decide(3L, confirmed, "coordinator");
        verify(evidence).audit(9L, "CHOOSE_AFTER_PICKUP_MISS", 3L,
                java.util.Map.of("choice", "RESCHEDULE_PICKUP", "phoneNote", "Phone confirmation"));
    }

    @Test
    void responsibilityIsRecordedOnlyOnceAndCannotBeChangedSilently() {
        service.record(3L, "Chip mismatch", NOW, 4L);
        AppUser manager = new AppUser();
        manager.setId(8L);
        Role role = new Role();
        role.setCode("MANAGER");
        manager.setRole(role);
        when(users.findByUsername("manager")).thenReturn(Optional.of(manager));
        var request = new PickupMissLiabilityRequestDto(PickupMissLiability.CUSTOMER);
        service.assignLiability(3L, request, "manager");
        service.assignLiability(3L, request, "manager");
        assertThatThrownBy(() -> service.assignLiability(3L,
                new PickupMissLiabilityRequestDto(PickupMissLiability.COMPANY), "manager"))
                .isInstanceOf(BookingRuleException.class);
        verify(evidence).audit(eq(8L), eq("CONFIRM_PICKUP_MISS_LIABILITY"), eq(3L), anyMap());
    }

    @Test
    void lastHorseCanBeMarkedForRemovalBeforeTheManagerConfirmsLiability() {
        service.record(3L, "Chip mismatch", NOW, 4L);
        var choice = new PickupMissChoiceRequestDto(PickupMissChoice.DROP_HORSE, null, null, null);
        service.decide(3L, choice, "owner");
        service.decide(3L, choice, "owner");
        assertThat(horse.getStatus()).isEqualTo(BookingHorseStatus.ACTIVE);
        verifyNoInteractions(cancellations);

        var manager = new AppUser();
        manager.setId(8L);
        var role = new Role();
        role.setCode("MANAGER");
        manager.setRole(role);
        when(users.findByUsername("manager")).thenReturn(Optional.of(manager));
        var liability = new PickupMissLiabilityRequestDto(PickupMissLiability.CUSTOMER);
        service.assignLiability(3L, liability, "manager");
        service.assignLiability(3L, liability, "manager");
        assertThat(horse.getStatus()).isEqualTo(BookingHorseStatus.EXCLUDED);
        verify(cancellations).cancelAfterPickupMiss(2L, true, 8L);
    }

    @Test
    void retryRequiresAConfirmedPlanAndUsesOneRetryPerDeparture() {
        trunk();
        service.record(3L, "Did not board", NOW, 4L);
        var request = new PickupMissChoiceRequestDto(PickupMissChoice.RETRY_PICKUP, null, null, null);
        assertThatThrownBy(() -> service.decide(3L, request, "owner")).isInstanceOf(BookingRuleException.class);
        var retryLeg = new HorseLeg();
        var trip = new VehicleTrip();
        trip.setPlannedStartAt(NOW.plus(Duration.ofHours(3)));
        var atHub = new TripStop();
        atHub.setPlannedArrivalAt(NOW.plus(Duration.ofHours(6)));
        retryLeg.setVehicleTrip(trip);
        retryLeg.setAlightTripStop(atHub);
        when(legs.findPickupRetryPlans(3L)).thenReturn(List.of(retryLeg));
        when(configs.getInt("PICKUP_RETRY_PREPARATION_HOURS")).thenReturn(2);
        service.decide(3L, request, "owner");
        assertThat(horse.getPickupRetryDeparture()).isSameAs(booking.getDeparture());

        service.record(3L, "Still did not board", NOW.plusSeconds(1), 4L);
        assertThatThrownBy(() -> service.decide(3L, request, "owner")).isInstanceOf(BookingRuleException.class);
    }

    @Test
    void finalCheckInDoesNotReleaseAHorseAlreadyReceived() {
        trunk();
        service.record(3L, "Did not board", NOW, 4L);
        var request = new PickupMissChoiceRequestDto(PickupMissChoice.SELF_DELIVERY, null, NOW.plusSeconds(3600), null);
        service.decide(3L, request, "owner");
        booking.getDeparture().setPlannedDepartureAt(NOW.plus(Duration.ofHours(3)));

        when(horses.receivedAfterPickupMiss(3L, NOW)).thenReturn(true);
        service.expire(3L);
        verifyNoInteractions(cancellations);

        when(horses.receivedAfterPickupMiss(3L, NOW)).thenReturn(false);
        service.expire(3L);
        service.expire(3L);
        verify(cancellations).releaseAfterPickupMiss(2L, false, null);
    }

    @Test
    void confirmedRetryCannotBoardWhenTheRevisedPlanMissesCheckIn() {
        trunk();
        horse.setPickupMissChoice(PickupMissChoice.RETRY_PICKUP);
        var leg = new HorseLeg();
        leg.setId(10L);
        leg.setBookingHorse(horse);
        var hub = new TripStop();
        leg.setAlightTripStop(hub);
        hub.setPlannedArrivalAt(NOW.plus(Duration.ofHours(26)));
        when(legs.existsByIdAndStatus(10L, HorseLegStatus.PLANNED)).thenReturn(true);
        service.requirePickupCanComplete(leg, NOW);

        hub.setPlannedArrivalAt(NOW.plus(Duration.ofHours(28)));
        assertThatThrownBy(() -> service.requirePickupCanComplete(leg, NOW))
                .isInstanceOf(DispatchRuleException.class);

        when(legs.existsByIdAndStatus(10L, HorseLegStatus.PLANNED)).thenReturn(false);
        assertThatThrownBy(() -> service.requirePickupCanComplete(leg, NOW))
                .isInstanceOf(DispatchRuleException.class).hasMessageContaining("đã được xử lý");
    }

    private void trunk() {
        Departure departure = new Departure();
        departure.setId(5L);
        departure.setPlannedDepartureAt(NOW.plus(Duration.ofHours(30)));
        booking.setDeparture(departure);
        booking.setBookingType(BookingType.INTERPROVINCE);
    }
}
