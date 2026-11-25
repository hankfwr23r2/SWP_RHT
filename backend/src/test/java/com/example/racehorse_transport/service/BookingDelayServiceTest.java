package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.dto.booking.CancelBookingRequestDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DepartureDelayPolicy.Thresholds;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.service.impl.BookingDelayServiceImpl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingDelayServiceTest {
    private static final Instant T = Instant.parse("2027-01-15T03:00:00Z");
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final DepartureRepository departures = mock(DepartureRepository.class);
    private final DepartureDelayService delays = mock(DepartureDelayService.class);
    private final BookingStatusService statuses = mock(BookingStatusService.class);
    private final BookingRescheduleService reschedules = mock(BookingRescheduleService.class);
    private final BookingCancellationService cancels = mock(BookingCancellationService.class);
    private final BookingDelayService service = new BookingDelayServiceImpl(bookings, mock(BookingStatusHistoryRepository.class),
            departures, delays, statuses, reschedules, cancels, Clock.fixed(T, ZoneOffset.UTC));
    private final Departure departure = new Departure();

    @BeforeEach
    void fixture() {
        departure.setId(9L);
        departure.setPlannedDepartureAt(T);
        departure.setStatus(DepartureStatus.CLOSED);
        departure.setDelayedAt(T);
        departure.setRevisedDepartureAt(T.plus(Duration.ofHours(5)));
        var user = new AppUser();
        user.setId(2L);
        user.setUsername("owner");
        var customer = new Customer();
        customer.setUser(user);
        var booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(customer);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setDeparture(departure);
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        when(bookings.findByIdAndCustomer_User_Username(1L, "owner")).thenReturn(Optional.of(booking));
        when(departures.findForUpdate(9L)).thenReturn(Optional.of(departure));
        when(delays.thresholds()).thenReturn(new Thresholds(24, 30, 2, 5));
    }

    @Test
    void refundUsesTheDelayedDepartureFlowAndChecksTheOwner() {
        var request = new CancelBookingRequestDto("Không đi nữa");
        var preview = mock(BookingCancellationResponseDto.class);
        when(preview.allowed()).thenReturn(true);
        when(cancels.previewForDelayedDeparture(any(Booking.class))).thenReturn(preview);

        assertThatThrownBy(() -> service.refund(1L, "stranger", request))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.view(1L, "owner")).singleElement().satisfies(options -> assertThat(options.canRefundFull()).isTrue());
        assertThat(service.refundPreview(1L, "owner")).isSameAs(preview);
        service.refund(1L, "owner", request);

        verify(cancels, times(2)).previewForDelayedDeparture(argThat(booking -> booking.getId() == 1L));
        verify(cancels).cancelForDelayedDeparture(1L, "owner", request);
    }

    @Test
    void hidesRefundWhenTheCancellationPreviewDisallowsIt() {
        var preview = mock(BookingCancellationResponseDto.class);
        when(cancels.previewForDelayedDeparture(any(Booking.class))).thenReturn(preview);

        assertThat(service.view(1L, "owner")).singleElement().satisfies(options -> {
            assertThat(options.canRefundFull()).isFalse();
            assertThat(options.canRescheduleFree()).isTrue();
        });
        assertThat(service.refundPreview(1L, "owner").allowed()).isFalse();
    }

    @Test
    void receivedConfirmedBookingIsHandledByManager() {
        when(bookings.hasReceivedHorses(1L)).thenReturn(true);
        assertThat(service.view(1L, "owner")).singleElement().satisfies(options -> {
            assertThat(options.requiresManagerContact()).isTrue();
            assertThat(options.canRefundFull()).isFalse();
            assertThat(options.canRescheduleFree()).isFalse();
        });
        assertThatThrownBy(() -> service.refund(1L, "owner", new CancelBookingRequestDto("Không đi nữa")))
                .isInstanceOf(BookingRuleException.class);
        verifyNoInteractions(cancels);
    }

    @Test
    void rescheduleRecordsCompanyCauseBeforeCallingTheExistingFlow() {
        when(departures.findForUpdate(10L)).thenReturn(Optional.of(new Departure()));
        var request = new BookingRescheduleRequestDto(10L, LocalDate.of(2027, 1, 18));
        service.reschedule(1L, "owner", request);
        var order = inOrder(statuses, reschedules);
        order.verify(statuses).requireScheduleChange(eq(1L), eq(departure), eq(ScheduleChangeCause.COMPANY), anyString(), eq(2L));
        order.verify(reschedules).reschedule(1L, "owner", request);
    }

    @Test
    void rescheduleAtOneHourAndRefundAfterDepartureAreRejected() {
        departure.setRevisedDepartureAt(T.plus(Duration.ofHours(1)));
        when(departures.findForUpdate(10L)).thenReturn(Optional.of(new Departure()));
        assertThatThrownBy(() -> service.reschedule(1L, "owner", new BookingRescheduleRequestDto(10L, LocalDate.of(2027, 1, 18))))
                .isInstanceOf(BookingRuleException.class);
        departure.setStatus(DepartureStatus.IN_PROGRESS);
        assertThatThrownBy(() -> service.refundPreview(1L, "owner")).isInstanceOf(BookingRuleException.class);
        verifyNoInteractions(reschedules, cancels);
    }
}
