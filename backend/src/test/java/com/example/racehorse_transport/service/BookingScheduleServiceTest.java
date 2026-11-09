package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.SystemConfigResponse;
import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.BookingSchedulePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingScheduleServiceTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final QuotationRepository quotations = mock(QuotationRepository.class);
    private final BookingVehicleRepository vehicles = mock(BookingVehicleRepository.class);
    private final BookingAssignmentRepository assignments = mock(BookingAssignmentRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final BillMailService mail = mock(BillMailService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-30T17:00:00Z"), ZoneOffset.UTC);
    private final BookingScheduleService service = new BookingScheduleService(bookings, payments, quotations,
            vehicles, assignments, notifications, config, mail, clock);
    private Booking booking;

    @BeforeEach
    void setup() {
        User user = new User();
        user.setId(12); user.setUsername("customer12"); user.setEmail("customer12@example.com");
        Customer customer = new Customer(); customer.setId(12); customer.setUser(user);
        booking = Booking.builder().id(3).customerID(customer).status("pending_intake")
                .departureDate(Instant.parse("2026-11-29T17:00:00Z"))
                .detailsJson("""
                    {"horses":[{"horseId":"H-1","name":"Test horse","insurance":{"option":"none","reviewed":true}}],
                     "medical":{"status":"approved"},"trips":[{"tripId":"T1"}],"plan":{"by":"coordinator"},
                     "route":{"legs":[]},"quote":{"total":1000000}}
                    """).build();
        when(bookings.findByIdForUpdate(3)).thenReturn(Optional.of(booking));
        when(payments.findByBookingID_IdOrderByPaymentDateDesc(3)).thenReturn(List.of());
        when(quotations.findByBookingIdOrderBySentAtDesc(3)).thenReturn(List.of());
        when(config.getConfig("MIN_LEAD_DAYS")).thenReturn(SystemConfigResponse.builder()
                .configKey("MIN_LEAD_DAYS").configValue("20").build());
    }

    @Test
    void deadlineIsTheEndOfTheVietnamCalendarDay() {
        assertThat(BookingSchedulePolicy.overdue(booking, Instant.parse("2026-11-30T16:59:59.999Z"))).isFalse();
        assertThat(BookingSchedulePolicy.overdue(booking, clock.instant())).isTrue();
        assertThatThrownBy(() -> BookingSchedulePolicy.requireCurrentSchedule(booking, clock.instant()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("xác nhận lại lịch");
    }

    @Test
    void flagsOnceAndReleasesReservationsWithoutDeletingCustomerDetails() {
        Quotation quote = Quotation.builder().status("PENDING").build();
        when(quotations.findByBookingIdOrderBySentAtDesc(3)).thenReturn(List.of(quote));
        service.flagOverdue(3);
        service.flagOverdue(3);
        assertThat(booking.getStatus()).isEqualTo(BookingSchedulePolicy.REQUIRED);
        assertThat(details().getHorses()).hasSize(1);
        assertThat(details().getScheduleChange()).containsEntry("previousStatus", "pending_intake");
        assertThat(quote.getStatus()).isEqualTo("EXPIRED");
        verify(vehicles).deleteByBookingID_Id(3);
        verify(assignments).deleteByBookingID_Id(3);
        verify(notifications, times(2)).save(any());
        verify(mail).scheduleChangeRequired(booking);
    }

    @Test
    void doesNotFlagDuringTheDepartureDayOrForAnOperationalTrip() {
        booking.setDepartureDate(Instant.parse("2026-11-30T17:00:00Z"));
        service.flagOverdue(3);
        booking.setDepartureDate(Instant.parse("2026-11-29T17:00:00Z"));
        booking.setStatus("in_transit");
        service.flagOverdue(3);
        verify(bookings, never()).save(any());
        verifyNoInteractions(notifications, mail);
    }

    @Test
    void neverReschedulesOrReleasesOrdersWithPendingOrReceivedMoney() {
        for (String status : List.of("PENDING", "COMPLETED", "NEEDS_REVIEW")) {
            booking.setStatus("awaiting_payment");
            when(payments.findByBookingID_IdOrderByPaymentDateDesc(3))
                    .thenReturn(List.of(Payment.builder().status(status).build()));
            service.flagOverdue(3);
            assertThat(booking.getStatus()).isEqualTo("awaiting_payment");
            booking.setStatus(BookingSchedulePolicy.REQUIRED);
            assertThatThrownBy(() -> service.reschedule(3, "customer12", LocalDate.of(2026, 12, 21)))
                    .hasMessageContaining("đối soát");
            assertThatThrownBy(() -> service.cancel(3, "customer12", "Cancel"))
                    .hasMessageContaining("đối soát");
        }
        verifyNoInteractions(vehicles, assignments);
    }

    @Test
    void reschedulePreservesHorsesAndReopensEveryReviewForTheNewDate() {
        service.flagOverdue(3);
        service.reschedule(3, "customer12", LocalDate.of(2026, 12, 21));
        assertThat(booking.getStatus()).isEqualTo("pending_intake");
        assertThat(booking.getDepartureDate()).isEqualTo(Instant.parse("2026-12-20T17:00:00Z"));
        BookingDetailsPayload changed = details();
        assertThat(changed.getScheduleChange()).isNull();
        assertThat(changed.getPlan()).isNull();
        assertThat(changed.getTrips()).isNull();
        assertThat(changed.getRoute()).isNull();
        assertThat(changed.getQuote()).isNull();
        assertThat(changed.getHorses().getFirst().getName()).isEqualTo("Test horse");
        assertThat(changed.getHorses().getFirst().getInsurance().getReviewed()).isFalse();
        assertThat(changed.getHistory()).hasSize(2);
    }

    @Test
    void enforcesOwnershipAndMinimumLeadTimeAndCurrentState() {
        service.flagOverdue(3);
        assertThatThrownBy(() -> service.reschedule(3, "other-customer", LocalDate.of(2026, 12, 21)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancel(3, "other-customer", "Cancel"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requestSupport(3, "other-customer"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reschedule(3, "customer12", LocalDate.of(2026, 12, 20)))
                .isInstanceOf(IllegalArgumentException.class);
        booking.setStatus("pending_intake");
        assertThatThrownBy(() -> service.reschedule(3, "customer12", LocalDate.of(2026, 12, 21)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void supportIsIdempotentAndKeepsTheOrderAvailableForAChoice() {
        service.flagOverdue(3);
        clearInvocations(notifications);
        service.requestSupport(3, "customer12");
        service.requestSupport(3, "customer12");
        assertThat(booking.getStatus()).isEqualTo(BookingSchedulePolicy.REQUIRED);
        assertThat(details().getScheduleChange()).containsKey("supportRequestedAt");
        verify(notifications).save(any());
    }

    @Test
    void cancellationRequiresConfirmationReasonAndRetainsTheHistory() {
        service.flagOverdue(3);
        assertThatThrownBy(() -> service.cancel(3, "customer12", " "))
                .isInstanceOf(IllegalArgumentException.class);
        service.cancel(3, "customer12", "Không còn nhu cầu");
        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(details().getHistory().getLast().get("text").toString()).contains("Không còn nhu cầu");
    }

    private BookingDetailsPayload details() {
        return BookingDetailsJson.read(new ObjectMapper(), booking);
    }
}
