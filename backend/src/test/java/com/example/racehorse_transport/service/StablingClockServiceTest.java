package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.dto.health.StablingClockResponseDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.PriceItemRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.service.CoordinatorRecipientService;
import com.example.racehorse_transport.service.impl.StablingClockServiceImpl;
import com.example.racehorse_transport.service.impl.StablingTimelineServiceImpl;
import com.example.racehorse_transport.policy.StablingPolicy.Run;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StablingClockServiceTest {
    private static final Instant NOW = Instant.parse("2031-01-14T03:00:00Z");
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final HorseStayRepository stays = mock(HorseStayRepository.class);
    private final CoordinatorRecipientService staff = mock(CoordinatorRecipientService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final StablingChargeService charges = mock(StablingChargeService.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final StablingClockService service = new StablingClockServiceImpl(bookings, stays, mock(HorseLegRepository.class),
            staff, charges, mock(RedeliveryChargeService.class), notifications,
            configs, Clock.fixed(NOW, ZoneOffset.UTC));
    private final HorseStay stay = new HorseStay();

    @BeforeEach
    void setUp() {
        var booking = new Booking();
        booking.setId(3L);
        var user = new AppUser();
        user.setUsername("owner");
        var customer = new Customer();
        customer.setUser(user);
        booking.setCustomer(customer);
        var horse = new Horse();
        horse.setName("Sao");
        var line = new BookingHorse();
        line.setBooking(booking);
        line.setHorse(horse);
        line.setId(7L);
        stay.setBookingHorse(line);
        stay.setId(4L);
        stay.setStayType(HorseStayType.STABLING);
        stay.setActualStartAt(NOW.minusSeconds(3600));
        when(stays.findBookingId(4L)).thenReturn(Optional.of(3L));
        when(bookings.findForUpdate(3L)).thenReturn(Optional.of(booking));
        when(stays.findForUpdate(4L)).thenReturn(Optional.of(stay));
        when(stays.findByBookingHorse_Booking_Id(3L)).thenReturn(List.of(stay));
    }

    @Test
    void coordinatorReadinessFinalizesHoldingFeesAndRecordsCall() {
        var actor = new Staff();
        actor.setUserId(15L);
        when(staff.staffByUsername("dispatcher")).thenReturn(actor);
        service.ready(4L, "dispatcher", "  Confirmed with customer  ");
        verify(charges).chargeThrough(stay, NOW, 15L);
        assertThat(stay.getReadyAt()).isEqualTo(NOW);
        assertThat(stay.getReadyCallNote()).isEqualTo("Confirmed with customer");
    }

    @Test
    void cannotRecordLaterChoiceBeforeReadiness() {
        assertThatThrownBy(() -> service.laterSlot(4L, NOW.plusSeconds(3600), NOW.plusSeconds(7200)))
                .isInstanceOf(DispatchRuleException.class);
        assertThat(stay.getEarliestRedeliveryAt()).isNull();
    }

    @Test
    void laterChoiceRecordsBothInstants() {
        stay.setReadyAt(NOW);
        var earliest = NOW.plusSeconds(3600);
        var chosen = NOW.plusSeconds(7200);
        service.laterSlot(4L, earliest, chosen);
        assertThat(stay.getEarliestRedeliveryAt()).isEqualTo(earliest);
        assertThat(stay.getChosenRedeliveryAt()).isEqualTo(chosen);
    }

    @Test
    void customerCanReportCollectionWithinTwentyFourHours() {
        when(configs.getInt("STABLING_COLLECTION_ADVANCE_HOURS")).thenReturn(24);
        var expected = NOW.plusSeconds(24 * 3600);
        service.collect(4L, "owner", expected);
        assertThat(stay.getCollectionReportedAt()).isEqualTo(NOW);
        assertThat(stay.getCollectionExpectedAt()).isEqualTo(expected);
    }

    @Test
    void customerCannotReportCollectionBeyondTwentyFourHours() {
        when(configs.getInt("STABLING_COLLECTION_ADVANCE_HOURS")).thenReturn(24);
        assertThatThrownBy(() -> service.collect(4L, "owner", NOW.plusSeconds(24 * 3600 + 1)))
                .isInstanceOf(DispatchRuleException.class);
        assertThat(stay.getCollectionReportedAt()).isNull();
    }

    @Test
    void coordinatorIsRemindedAfterTwentyFourHoursWithoutAVehicleTrip() {
        when(configs.getInt("STABLING_REDELIVERY_OFFER_HOURS")).thenReturn(24);
        when(stays.findByReadyAtIsNotNullAndActualEndAtIsNull()).thenReturn(List.of(stay));
        stay.setReadyAt(NOW.minusSeconds(24 * 3600));
        stay.setEarliestRedeliveryAt(NOW);
        stay.setChosenRedeliveryAt(NOW.plusSeconds(7200));
        var recipients = List.of(15L);
        when(staff.forHorse(stay.getBookingHorse(), null)).thenReturn(recipients);
        service.remindMissingRedelivery();
        verify(notifications).notify(eq(recipients), eq(3L), anyString(), anyString(), eq("/coordinator/hub"));
    }

    @Test
    void coordinatorIsNotRemindedBeforeTheOfferDeadline() {
        when(configs.getInt("STABLING_REDELIVERY_OFFER_HOURS")).thenReturn(24);
        when(stays.findByReadyAtIsNotNullAndActualEndAtIsNull()).thenReturn(List.of(stay));
        stay.setReadyAt(NOW.minusSeconds(23 * 3600));
        service.remindMissingRedelivery();
        verifyNoInteractions(notifications);
    }

    @Test
    void balanceHoldWaitsForActualLinehaulDepartureWhenItStartsLate() {
        var missed = new Departure();
        missed.setId(9L);
        stay.setMissedDeparture(missed);
        stay.setBalanceHoldAt(NOW);
        when(stays.findByBookingHorse_IdInOrderById(List.of(7L))).thenReturn(List.of(stay));
        var trips = mock(VehicleTripRepository.class);
        var actualStart = NOW.plusSeconds(7200);
        var timeline = new StablingTimelineServiceImpl(stays, mock(CostItemRepository.class), trips, configs,
                mock(BookingStatusHistoryRepository.class), mock(PaymentRepository.class), mock(QuotationRepository.class),
                mock(PriceItemRepository.class), mock(PaymentReadinessService.class), mock(ExtraChargeService.class));

        assertThat(timeline.runs(stay, NOW)).isEmpty();
        when(trips.firstActualStartForDeparture(9L)).thenReturn(Optional.of(actualStart));
        assertThat(timeline.runs(stay, actualStart)).containsExactly(new Run(actualStart, null));
        assertThat(stay.getBalanceHoldAt()).isEqualTo(NOW);
    }

    @Test
    void dtoUsesServerComputedElapsedTimeAndFee() {
        stay.setReadyAt(NOW);
        var amount = new BigDecimal("2000000");
        var result = StablingClockResponseDto.from(stay, false, 7200, amount, null, false, true);
        assertThat(result.stayId()).isEqualTo(4L);
        assertThat(result.horseName()).isEqualTo("Sao");
        assertThat(result.readyAt()).isEqualTo(NOW);
        assertThat(result.elapsedSeconds()).isEqualTo(7200);
        assertThat(result.amount()).isEqualByComparingTo(amount);
        assertThat(result.running()).isFalse();
        assertThat(result.canReady()).isFalse();
        assertThat(result.canCollect()).isTrue();
    }
}
