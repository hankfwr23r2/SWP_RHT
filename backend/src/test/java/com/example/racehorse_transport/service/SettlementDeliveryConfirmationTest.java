package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.impl.SettlementServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettlementDeliveryConfirmationTest {

    private static final long BOOKING_ID = 42L;
    private static final String MANAGER = "manager.test";
    private static final Instant DELIVERED_AT = Instant.parse("2026-11-25T08:00:00Z");
    private static final Instant DEADLINE = DELIVERED_AT.plus(Duration.ofDays(7));

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingStatusHistoryRepository history = mock(BookingStatusHistoryRepository.class);
    private final QuotationRepository quotations = mock(QuotationRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final CostItemRepository costs = mock(CostItemRepository.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final BookingStatusService statuses = mock(BookingStatusService.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final Booking booking = new Booking();

    @BeforeEach
    void setUpDeliveredBooking() {
        booking.setId(BOOKING_ID);
        booking.setStatus(BookingStatus.DELIVERED);
        when(bookings.findForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        var delivery = new BookingStatusHistory();
        delivery.setChangedAt(DELIVERED_AT);
        when(history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(BOOKING_ID, BookingStatus.DELIVERED))
                .thenReturn(Optional.of(delivery));
        when(configs.getInt("SETTLEMENT_AUTO_CLOSE_DAYS")).thenReturn(7);
        var quotation = new Quotation();
        quotation.setCurrencyCode("VND");
        quotation.setTotalAmount(BigDecimal.ZERO);
        when(quotations.findByBooking_IdAndStatus(BOOKING_ID, QuotationStatus.ACCEPTED))
                .thenReturn(Optional.of(quotation));
        when(costs.sumApprovedForBooking(eq(BOOKING_ID), org.mockito.ArgumentMatchers.anySet())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void confirmedBookingCanBeClosedByManagerBeforeDeadline() {
        booking.setDeliveryConfirmedAt(DELIVERED_AT.plusSeconds(1));

        serviceAt(DELIVERED_AT.plus(Duration.ofDays(1))).complete(BOOKING_ID, MANAGER);

        verify(statuses).transitionForUser(eq(BOOKING_ID), eq(BookingStatus.COMPLETED), anyString(), eq(MANAGER));
    }

    @Test
    void unconfirmedBookingCannotBeClosedAtDeadline() {
        var service = serviceAt(DEADLINE);

        assertThatThrownBy(() -> service.complete(BOOKING_ID, MANAGER))
                .isInstanceOf(BookingRuleException.class)
                .hasMessageContaining("hết hạn");
        assertThat(service.autoClose(BOOKING_ID)).isFalse();
        verifyNoInteractions(statuses);
    }

    @Test
    void unconfirmedBookingCanBeAutomaticallyClosedAfterDeadline() {
        assertThat(serviceAt(DEADLINE.plusSeconds(1)).autoClose(BOOKING_ID)).isTrue();

        verify(statuses).transition(eq(BOOKING_ID), eq(BookingStatus.COMPLETED), anyString(), isNull());
    }

    @Test
    void openComplaintBlocksManagerAndAutomaticClosureAfterDeadline() {
        when(incidents.hasIncidentForBookingOtherThan(BOOKING_ID, IncidentStatus.CLOSED)).thenReturn(true);
        var service = serviceAt(DEADLINE.plusSeconds(1));

        assertThatThrownBy(() -> service.complete(BOOKING_ID, MANAGER))
                .isInstanceOf(BookingRuleException.class)
                .hasMessageContaining("sự cố mở");
        assertThat(service.autoClose(BOOKING_ID)).isFalse();
        verifyNoInteractions(statuses);
    }

    @Test
    void unpaidExtraChargeDoesNotBlockClosure() {
        when(costs.findByBooking_IdOrderByIncurredAt(BOOKING_ID)).thenReturn(java.util.List.of(cost(true, true)));
        when(costs.sumApprovedForBooking(eq(BOOKING_ID), org.mockito.ArgumentMatchers.anySet())).thenReturn(BigDecimal.TEN);

        assertThat(serviceAt(DEADLINE.plusSeconds(1)).autoClose(BOOKING_ID)).isTrue();
    }

    @Test
    void costWithoutApprovalOrChargeBlocksClosure() {
        var service = serviceAt(DEADLINE.plusSeconds(1));
        when(costs.findByBooking_IdOrderByIncurredAt(BOOKING_ID)).thenReturn(java.util.List.of(cost(false, false)));
        assertThat(service.autoClose(BOOKING_ID)).isFalse();
        when(costs.findByBooking_IdOrderByIncurredAt(BOOKING_ID)).thenReturn(java.util.List.of(cost(true, false)));
        assertThat(service.autoClose(BOOKING_ID)).isFalse();

        var declined = cost(false, false);
        declined.setCustomerAgreed(false);
        when(costs.findByBooking_IdOrderByIncurredAt(BOOKING_ID)).thenReturn(java.util.List.of(declined));
        assertThat(service.autoClose(BOOKING_ID)).isTrue();
    }

    private static com.example.racehorse_transport.entity.CostItem cost(boolean approved, boolean charged) {
        var cost = new com.example.racehorse_transport.entity.CostItem();
        cost.setId(5L);
        cost.setPayerType(com.example.racehorse_transport.entity.enums.CostPayerType.CUSTOMER);
        cost.setCurrencyCode("VND");
        cost.setAmount(BigDecimal.TEN);
        if (approved) cost.setApprovedAt(DELIVERED_AT);
        if (charged) cost.setExtraChargePayment(new com.example.racehorse_transport.entity.Payment());
        return cost;
    }

    private SettlementServiceImpl serviceAt(Instant now) {
        return new SettlementServiceImpl(bookings, history, quotations, payments, costs, incidents, statuses,
                mock(PaymentService.class), configs, Clock.fixed(now, ZoneOffset.UTC));
    }
}
