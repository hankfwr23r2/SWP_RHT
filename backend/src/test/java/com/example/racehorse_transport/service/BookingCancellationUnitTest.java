package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.dto.booking.CompanyCancelBookingRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.impl.BookingCancellationServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingCancellationUnitTest {
    private static final Instant NOW = Instant.parse("2027-01-14T10:00:00Z");
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingHorseRepository horses = mock(BookingHorseRepository.class);
    private final QuotationRepository quotations = mock(QuotationRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final BookingStatusService statuses = mock(BookingStatusService.class);
    private final DepartureService departures = mock(DepartureService.class);
    private final RefundService refunds = mock(RefundService.class);
    private final PaymentCheckoutService checkout = mock(PaymentCheckoutService.class);
    private final ScheduleChangeService schedules = mock(ScheduleChangeService.class);
    private final BookingCancellationService cancellations = new BookingCancellationServiceImpl(
            bookings, horses, quotations, payments, statuses, departures, refunds, checkout, schedules);
    private final Payment balance = payment(PaymentType.BALANCE, PaymentStatus.PENDING, "6000");

    @BeforeEach
    void confirmedBookingWithUnpaidBalance() {
        var booking = new Booking();
        booking.setId(1L);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        var quotation = new Quotation();
        quotation.setId(2L);
        quotation.setCurrencyCode("VND");
        quotation.setDepositAmount(new BigDecimal("4000"));
        quotation.setTotalAmount(new BigDecimal("10000"));
        when(quotations.findByBooking_IdAndStatus(1L, QuotationStatus.ACCEPTED)).thenReturn(Optional.of(quotation));
        when(payments.findByBooking_IdOrderById(1L)).thenReturn(List.of(
                payment(PaymentType.DEPOSIT, PaymentStatus.PAID, "4000"), balance));
        when(checkout.isBalanceOverdue(org.mockito.ArgumentMatchers.anyList())).thenReturn(true);
        balance.setDueAt(NOW.minusSeconds(300));
    }

    @Test
    void acceptsWhenNoBalanceSessionStillAwaitsConfirmation() {
        when(checkout.hasPendingConfirmation(1L)).thenReturn(true);
        cancellations.cancelUnpaidAtPickup(1L, 3L);

        assertThat(balance.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(statuses).transition(1L, BookingStatus.CANCELLED,
                "Quá thời gian chờ thanh toán số dư tại nơi nhận ngựa.", 3L);
        verifyNoInteractions(refunds);
    }

    @Test
    void stillWaitsWhileBalanceConfirmationWindowIsOpen() {
        balance.setDueAt(NOW.minusSeconds(1800));
        when(checkout.hasPendingBalanceConfirmation(1L)).thenReturn(true);

        assertThatThrownBy(() -> cancellations.cancelUnpaidAtPickup(1L, 3L))
                .isInstanceOf(BookingRuleException.class);
        assertThat(balance.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(statuses, departures, refunds);
    }

    @Test
    void companyCancellationRefundsEverythingEvenAfterACustomerDelay() {
        var booking = bookings.findForUpdate(1L).orElseThrow();
        booking.setStatus(BookingStatus.SCHEDULE_CHANGE_REQUIRED);
        when(payments.findByBooking_IdOrderById(1L)).thenReturn(List.of(payment(PaymentType.DEPOSIT, PaymentStatus.PAID, "4000")));

        cancellations.cancelForCompany(1L, "manager", new CompanyCancelBookingRequestDto("Không có xe"));

        verify(refunds).createPending(1L, "VND", new BigDecimal("4000"));
        verifyNoInteractions(schedules);
    }

    private static Payment payment(PaymentType type, PaymentStatus status, String amount) {
        var row = new Payment();
        row.setPaymentType(type);
        row.setStatus(status);
        row.setAmount(new BigDecimal(amount));
        row.setCurrencyCode("VND");
        return row;
    }
}
