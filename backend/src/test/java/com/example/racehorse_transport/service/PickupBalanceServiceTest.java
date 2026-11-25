package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.dto.operation.BalanceHoldContactRequestDto;
import com.example.racehorse_transport.dto.operation.PickupHubTransferRequestDto;
import com.example.racehorse_transport.dto.operation.PickupWaitExtensionRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.AuditLogRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.impl.PickupBalanceServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PickupBalanceServiceTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final TripStopRepository stops = mock(TripStopRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final AuditLogRepository logs = mock(AuditLogRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final PaymentCheckoutService checkout = mock(PaymentCheckoutService.class);
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final Instant now = Instant.parse("2026-11-25T08:00:00Z");
    private final PickupBalanceService service = new PickupBalanceServiceImpl(bookings, legs, payments, stops, staff,
            logs, audit, checkout, config, Clock.fixed(now, ZoneOffset.UTC));
    private final Payment balance = new Payment();
    private final HorseLeg leg = new HorseLeg();
    private final TripStop hub = new TripStop();

    @BeforeEach
    void preparePickup() {
        var booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        when(bookings.findWaitingPickupSince(1L)).thenReturn(Optional.of(now.minusSeconds(900)));
        when(config.getInt("BALANCE_WAIT_MINUTES")).thenReturn(30);
        when(config.getInt("MAX_DRIVE_HOURS_PER_DAY")).thenReturn(10);
        var coordinator = new Staff();
        coordinator.setUserId(2L);
        when(staff.findByUser_Username("dispatch")).thenReturn(Optional.of(coordinator));
        balance.setPaymentType(PaymentType.BALANCE);
        balance.setStatus(PaymentStatus.PENDING);
        balance.setDueAt(now.plusSeconds(900));
        when(payments.findByBooking_IdOrderById(1L)).thenReturn(List.of(balance));

        var trip = new VehicleTrip();
        trip.setId(3L);
        trip.setTripType(VehicleTripType.DIRECT);
        var pickup = new TripStop();
        pickup.setId(4L);
        pickup.setSequenceNumber(1);
        pickup.setStopType(TripStopType.PICKUP);
        pickup.setPlannedDepartureAt(now);
        pickup.setVehicleTrip(trip);
        hub.setId(5L);
        hub.setSequenceNumber(2);
        hub.setStopType(TripStopType.HUB);
        hub.setLocation(new Location());
        hub.setPlannedArrivalAt(now.plusSeconds(1800));
        leg.setStatus(HorseLegStatus.IN_PROGRESS);
        leg.setBoardTripStop(pickup);
        leg.setAlightTripStop(hub);
        leg.setActualStartAt(now.minusSeconds(900));
        when(legs.findByBookingHorse_Booking_IdAndStatus(1L, HorseLegStatus.IN_PROGRESS)).thenReturn(List.of(leg));
        when(legs.findActiveOnTrip(eq(3L), any())).thenReturn(List.of(leg));
        when(stops.findByVehicleTrip_IdOrderBySequenceNumber(3L)).thenReturn(List.of(pickup, hub));
    }

    @Test
    void extensionUpdatesTheDueTimeExactlyOnce() {
        when(logs.existsByTargetTableAndTargetIdAndActionAndCreatedAtGreaterThanEqual(
                eq("booking"), eq(1L), eq("PICKUP_BALANCE_WAIT_EXTENDED"), any())).thenReturn(false, true);
        var request = new PickupWaitExtensionRequestDto("Khách xin thêm thời gian.");
        service.extend(1L, request, "dispatch");
        assertThat(balance.getDueAt()).isEqualTo(now.plusSeconds(2700));
        assertThatThrownBy(() -> service.extend(1L, request, "dispatch")).isInstanceOf(BookingRuleException.class);
        verify(audit, times(1)).record(eq(2L), eq("PICKUP_BALANCE_WAIT_EXTENDED"), eq("booking"), eq(1L), any(), any());
    }

    @Test
    void transferRequiresAnOverdueBalanceAndNoPendingGatewayResult() {
        var request = new PickupHubTransferRequestDto("Không còn người nhận lại ngựa.");
        assertThatThrownBy(() -> service.transferToHub(1L, request, "dispatch")).isInstanceOf(BookingRuleException.class);
        balance.setDueAt(now.minusSeconds(1));
        when(checkout.hasPendingBalanceConfirmation(1L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.transferToHub(1L, request, "dispatch")).isInstanceOf(BookingRuleException.class);
        service.transferToHub(1L, request, "dispatch");
        assertThat(leg.getToLocation()).isSameAs(hub.getLocation());
        verify(audit, times(1)).record(eq(2L), eq("UNCLAIMED_PICKUP_TO_HUB"), eq("booking"), eq(1L), any(), any());
    }

    @Test
    void contactLogsRequireCustodyAndIgnoreAnImmediateRepeat() {
        var request = new BalanceHoldContactRequestDto(BalanceHoldContactRequestDto.Target.CUSTOMER, "Chưa nghe máy.");
        assertThatThrownBy(() -> service.recordContact(1L, request, "dispatch")).isInstanceOf(BookingRuleException.class);
        when(bookings.hasReceivedHorses(1L)).thenReturn(true);
        when(logs.existsByTargetTableAndTargetIdAndActionAndCreatedAtGreaterThanEqual(
                eq("booking"), eq(1L), eq("BALANCE_HOLD_CALL_CUSTOMER"), any())).thenReturn(false, true);
        service.recordContact(1L, request, "dispatch");
        service.recordContact(1L, request, "dispatch");
        verify(audit, times(1)).record(eq(2L), eq("BALANCE_HOLD_CALL_CUSTOMER"), eq("booking"), eq(1L), any(), any());
    }
}
