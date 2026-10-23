package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.payment.CancelBookingRequest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceImplTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final QuotationRepository quotationRepository = mock(QuotationRepository.class);

    private PaymentServiceImpl service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, bookingRepository, quotationRepository, new ObjectMapper());

        User user = new User();
        user.setId(7);
        user.setUsername("phuc");
        user.setEmail("phuc@example.com");
        Customer customer = new Customer();
        customer.setId(7);
        customer.setUser(user);

        booking = new Booking();
        booking.setId(42);
        booking.setCustomerID(customer);
        booking.setStatus("clearance_in_progress");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));

        Payment deposit = Payment.builder().id(1).bookingID(booking).amount(new BigDecimal("3000000"))
                .paymentType("DEPOSIT").status("COMPLETED").build();
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(42)).thenReturn(List.of(deposit));
        when(paymentRepository.findById(1)).thenReturn(Optional.of(deposit));
    }

    @Test
    void cancel_byAnotherCustomer_isDenied() {
        assertThatThrownBy(() -> service.cancelWithRefundPolicy("BK-42", CancelBookingRequest.builder().reason("Đổi lịch").build(), "khac"))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(booking.getStatus()).isEqualTo("clearance_in_progress");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancel_whileInTransit_isRejected() {
        booking.setStatus("in_transit");

        assertThatThrownBy(() -> service.cancelWithRefundPolicy("BK-42", CancelBookingRequest.builder().reason("Đổi lịch").build(), "phuc"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(booking.getStatus()).isEqualTo("in_transit");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancel_byOwner_recordsTheSignedInUserAsActor() {
        service.cancelWithRefundPolicy("BK-42", CancelBookingRequest.builder().reason("Đổi lịch").build(), "phuc");

        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(booking.getDetailsJson()).contains("\"actor\":\"phuc\"");
    }

    @Test
    void cancel_afterDeposit_recordsWhenAndWhyForTheCustomerViews() {
        service.cancelWithRefundPolicy("BK-42", CancelBookingRequest.builder().reason("Đổi lịch").build(), "phuc");

        assertThat(booking.getDetailsJson())
                .contains("\"cancelled\":true")
                .contains("\"cancelReason\":\"Đổi lịch\"")
                .contains("\"refundAmount\":0")
                .containsPattern("\"cancelledAt\":\\d{13}");
    }

    @Test
    void paymentViews_forAnotherCustomer_areDenied() {
        assertThatThrownBy(() -> service.getPaymentSummary("BK-42", "khac")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getPaymentsByBooking("BK-42", "khac")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getPaymentDetail(1, "khac")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void paymentViews_forOwnerOrStaff_areAllowed() {
        assertThat(service.getPaymentSummary("BK-42", "phuc@example.com").getBookingId()).isEqualTo(42);
        assertThat(service.getPaymentsByBooking("BK-42", null)).hasSize(1);
        assertThat(service.getPaymentDetail(1, "phuc").getId()).isEqualTo(1);
    }

    @Test
    void cancel_afterBalancePaid_isRejected() {
        booking.setStatus("ready_for_pickup");
        Payment deposit = Payment.builder().id(1).bookingID(booking).amount(new BigDecimal("3000000"))
                .paymentType("DEPOSIT").status("COMPLETED").build();
        Payment balance = Payment.builder().id(2).bookingID(booking).amount(new BigDecimal("7000000"))
                .paymentType("BALANCE").status("COMPLETED").build();
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(42)).thenReturn(List.of(balance, deposit));

        assertThatThrownBy(() -> service.cancelWithRefundPolicy("BK-42", CancelBookingRequest.builder().reason("Đổi lịch").build(), "phuc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hotline");
        assertThat(booking.getStatus()).isEqualTo("ready_for_pickup");
        verify(bookingRepository, never()).save(any());
    }
}
