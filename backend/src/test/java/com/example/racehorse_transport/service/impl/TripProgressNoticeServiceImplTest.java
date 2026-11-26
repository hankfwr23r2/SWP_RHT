package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.policy.DeliveryEtaPolicy;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.service.CoordinatorRecipientService;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class TripProgressNoticeServiceImplTest {
    private final Instant hubDeparture = Instant.parse("2026-11-24T23:00:00Z");
    private final Instant heldNow = hubDeparture.plusSeconds(9000);
    private final VehicleTrip trip = new VehicleTrip();
    private final Booking booking = new Booking();
    private final VehicleTripRepository trips = mock(VehicleTripRepository.class);
    private final TripStopRepository stops = mock(TripStopRepository.class);
    private final CoordinatorRecipientService staff = mock(CoordinatorRecipientService.class);
    private final HorseLegRepository legs = mock(HorseLegRepository.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final TripProgressNoticeServiceImpl service = new TripProgressNoticeServiceImpl(trips, stops,
            legs, staff, notifications, configs,
            Clock.fixed(heldNow, ZoneOffset.UTC), transactions);

    @BeforeEach
    void setUp() {
        trip.setId(1L);
        trip.setTripType(VehicleTripType.DELIVERY);
        trip.setStatus(VehicleTripStatus.IN_PROGRESS);
        var customer = new Customer();
        var user = new AppUser();
        user.setId(9L);
        customer.setUser(user);
        booking.setCustomer(customer);
        booking.setId(3L);
        when(configs.getInt(DeliveryEtaPolicy.NOTICE_THRESHOLD)).thenReturn(30);
        when(configs.getInt(DeliveryEtaPolicy.TOLERANCE)).thenReturn(30);
        when(configs.getInt("DELIVERY_WINDOW_END_HOUR")).thenReturn(18);
        when(configs.getInt("MAX_DRIVE_HOURS_PER_DAY")).thenReturn(10);
    }

    @Test
    void warnsCoordinatorsOncePerTripForEachExceededLimit() {
        var hub = stop(1, TripStopType.HUB, -1800);
        var delivery = stop(2, TripStopType.DELIVERY, 4 * 3600);
        var horse = new HorseLeg();
        horse.setActualStartAt(hubDeparture);
        horse.setAlightTripStop(delivery);
        hub.setActualDepartureAt(hubDeparture.plusSeconds(9 * 3600));
        when(stops.findByVehicleTrip_IdOrderBySequenceNumber(1L)).thenReturn(List.of(hub, delivery));
        when(legs.findActiveOnTrip(eq(1L), any())).thenReturn(List.of(horse));
        when(staff.forTrip(trip)).thenReturn(List.of(8L));

        service.departed(hub);
        service.departed(hub);

        assertThat(trip.getDeliveryWindowWarnedAt()).isEqualTo(hub.getActualDepartureAt());
        assertThat(trip.getDeliveryRideWarnedAt()).isEqualTo(hub.getActualDepartureAt());
        verify(notifications, times(2)).notify(eq(List.of(8L)), isNull(), anyString(),
                argThat(message -> message.contains("Point 2")), isNull());
    }

    @Test
    void onTimeDepartureConfirmsThePlanWithoutANotice() {
        var hub = stop(1, TripStopType.HUB, -1800);
        var a = stop(2, TripStopType.DELIVERY, 3600);
        hub.setActualDepartureAt(hubDeparture);
        when(stops.findByVehicleTrip_IdOrderBySequenceNumber(1L)).thenReturn(List.of(hub, a));

        service.departed(hub);

        assertThat(a.getExpectedArrivalAt()).isEqualTo(a.getPlannedArrivalAt());
        assertThat(a.getToldArrivalAt()).isEqualTo(a.getPlannedArrivalAt());
        assertThat(a.getToldAt()).isEqualTo(hubDeparture);
        verifyNoInteractions(notifications);
    }

    @Test
    void heldTripSendsOneNoticeForTwoStopsAndSameEtaDoesNotResend() {
        var hub = stop(1, TripStopType.HUB, -1800);
        var a = stop(2, TripStopType.DELIVERY, 3600);
        var b = stop(3, TripStopType.DELIVERY, 16200);
        var c = stop(4, TripStopType.DELIVERY, 19800);
        a.setActualArrivalAt(a.getPlannedArrivalAt());
        when(stops.findByVehicleTrip_IdOrderBySequenceNumber(1L)).thenReturn(List.of(hub, a, b, c));
        when(trips.findHeldTripIds(eq(VehicleTripStatus.IN_PROGRESS), any())).thenReturn(List.of(1L));
        when(trips.findForUpdate(1L)).thenReturn(Optional.of(trip));
        doAnswer(call -> {
            call.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0)
                    .accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());

        service.checkHeldTrips();
        assertThat(b.getToldArrivalAt()).isNull();

        hub.setActualDepartureAt(hubDeparture);
        service.departed(hub);
        service.checkHeldTrips();
        service.checkHeldTrips();
        a.setActualDepartureAt(heldNow);
        service.departed(a);

        assertThat(b.getToldArrivalAt()).isEqualTo(hubDeparture.plusSeconds(19800));
        assertThat(c.getToldArrivalAt()).isEqualTo(hubDeparture.plusSeconds(23400));
        assertThat(b.getToldAt()).isEqualTo(heldNow);
        verify(notifications).notify(eq(List.of(9L)), eq(3L), anyString(),
                argThat(message -> message.contains("Point 3") && message.contains("Point 4")), eq("/orders/3"));
        verifyNoMoreInteractions(notifications);
    }

    private TripStop stop(int sequence, TripStopType type, long elapsed) {
        var stop = new TripStop();
        stop.setId((long) sequence);
        stop.setVehicleTrip(trip);
        stop.setSequenceNumber(sequence);
        stop.setStopType(type);
        stop.setPlannedArrivalAt(hubDeparture.plusSeconds(elapsed));
        stop.setPlannedDepartureAt(stop.getPlannedArrivalAt().plusSeconds(1800));
        if (type == TripStopType.DELIVERY) {
            var address = new BookingAddress();
            address.setAddressLine("Point " + sequence);
            address.setBooking(booking);
            stop.setBookingAddress(address);
        }
        return stop;
    }
}
