package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.payment.CancelBookingRequestDto;
import com.example.racehorse_transport.dto.payment.ReconcileInsuranceRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.StaffNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
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

    private final StaffNotificationService staffNotifier =
            mock(StaffNotificationService.class);
    private final BookingService bookingService =
            mock(BookingService.class);
    private PaymentServiceImpl service;
    private Booking booking;

    private static final String CODE = "BK-7F3K9QXM";

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, bookingRepository, quotationRepository, new ObjectMapper(), staffNotifier, bookingService);

        User user = new User();
        user.setId(7);
        user.setUsername("phuc");
        user.setEmail("phuc@example.com");
        Customer customer = new Customer();
        customer.setId(7);
        customer.setUser(user);

        booking = new Booking();
        booking.setId(42);
        booking.setCode(CODE);
        when(bookingRepository.findIdByCode(CODE)).thenReturn(Optional.of(42));
        booking.setCustomerID(customer);
        booking.setStatus("clearance_in_progress");
        when(bookingRepository.findById(42)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));

        Payment deposit = Payment.builder().id(1).bookingID(booking).amount(new BigDecimal("3000000"))
                .paymentType("DEPOSIT").status("COMPLETED").build();
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(42)).thenReturn(List.of(deposit));
        when(paymentRepository.findById(1)).thenReturn(Optional.of(deposit));
    }

    @Test
    void cancellationSeparatesInsuranceFromTransportPenaltyAndReconcilesOnlyOnce() {
        booking.setDetailsJson("""
            {"quote":{"total":10000000,"lines":[{"label":"Thu hộ bảo hiểm bên thứ ba","amount":2000000}]}}
            """);
        var result = service.cancelWithRefundPolicy(CODE, CancelBookingRequestDto.builder().reason("Đổi lịch").build(), "phuc");
        assertThat(result.getPenaltyAmount()).isEqualByComparingTo("2400000");
        assertThat(booking.getDetailsJson()).contains("\"insuranceCollected\":600000", "\"insuranceReconciliationPending\":true");
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        var request = new ReconcileInsuranceRequestDto(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("600000"), "REF-42", "Chưa chuyển đối tác; đã hoàn khách");
        service.reconcileInsurance(CODE, request, "manager");
        assertThat(booking.getDetailsJson()).contains("\"insuranceReconciliationPending\":false", "\"refundAmount\":600000");
        assertThatThrownBy(() -> service.reconcileInsurance(CODE, request, "manager")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void insuranceReconciliationRejectsInflatedRefundWithoutRecordingPayment() {
        booking.setStatus("cancelled");
        booking.setDetailsJson("""
            {"payment":{"insuranceCollected":600000,"insuranceReconciliationPending":true},"quote":{"total":10000000,"lines":[{"label":"Thu hộ bảo hiểm bên thứ ba","amount":2000000}]}}
            """);
        when(bookingRepository.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        var request = new ReconcileInsuranceRequestDto(new BigDecimal("500000"), BigDecimal.ZERO, new BigDecimal("600000"), "REF-42", "Đối soát");
        assertThatThrownBy(() -> service.reconcileInsurance(CODE, request, "manager")).isInstanceOf(IllegalArgumentException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void cancel_byAnotherCustomer_isDenied() {
        assertThatThrownBy(() -> service.cancelWithRefundPolicy(CODE, CancelBookingRequestDto.builder().reason("Đổi lịch").build(), "khac"))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(booking.getStatus()).isEqualTo("clearance_in_progress");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancel_whileInTransit_isRejected() {
        booking.setStatus("in_transit");

        assertThatThrownBy(() -> service.cancelWithRefundPolicy(CODE, CancelBookingRequestDto.builder().reason("Đổi lịch").build(), "phuc"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(booking.getStatus()).isEqualTo("in_transit");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancel_byOwner_recordsTheSignedInUserAsActor() {
        service.cancelWithRefundPolicy(CODE, CancelBookingRequestDto.builder().reason("Đổi lịch").build(), "phuc");

        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(booking.getDetailsJson()).contains("\"actor\":\"phuc\"");
    }

    @Test
    void cancel_afterDeposit_recordsWhenAndWhyForTheCustomerViews() {
        service.cancelWithRefundPolicy(CODE, CancelBookingRequestDto.builder().reason("Đổi lịch").build(), "phuc");

        assertThat(booking.getDetailsJson())
                .contains("\"cancelled\":true")
                .contains("\"cancelReason\":\"Đổi lịch\"")
                .contains("\"refundAmount\":0")
                .containsPattern("\"cancelledAt\":\\d{13}");
        // Kíp xe và điều phối được báo hủy, không chỉ Quản lý
        org.mockito.Mockito.verify(staffNotifier).bookingCancelled(org.mockito.ArgumentMatchers.eq(booking),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("Đổi lịch"));
    }

    @Test
    void cancel_afterTheCrewHasTheHorses_isRejectedForTheCustomer() {
        booking.setStatus("en_route_to_pickup");
        when(bookingService.isPickupComplete(booking)).thenReturn(true);

        assertThatThrownBy(() -> service.cancelWithRefundPolicy(CODE, CancelBookingRequestDto.builder().reason("Đổi ý").build(), "phuc"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("đã nhận ngựa");
        assertThat(booking.getStatus()).isEqualTo("en_route_to_pickup");
    }

    @Test
    void withdraw_beforeDeposit_isFreeAndNotifiesStaff() {
        booking.setStatus("pending_intake");
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(42)).thenReturn(List.of());

        var response = service.cancelWithRefundPolicy(CODE, CancelBookingRequestDto.builder().reason("Không cần nữa").build(), "phuc");

        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(response.getPenaltyAmount()).isZero();
        assertThat(booking.getDetailsJson()).contains("Rút yêu cầu vận chuyển");
        verify(staffNotifier).bookingCancelled(org.mockito.ArgumentMatchers.eq(booking), any(), org.mockito.ArgumentMatchers.eq("Không cần nữa"));
    }

    @Test
    void cancelTrip_byCompany_refundsEverythingCollected() {
        Payment deposit = Payment.builder().id(1).bookingID(booking).amount(new BigDecimal("3000000"))
                .paymentType("DEPOSIT").status("COMPLETED").build();
        Payment balance = Payment.builder().id(2).bookingID(booking).amount(new BigDecimal("7000000"))
                .paymentType("BALANCE").status("COMPLETED").build();
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(42)).thenReturn(List.of(balance, deposit));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.cancelTrip(CODE, "COMPANY", "Xe hỏng, không có xe thay", "manager01");

        org.mockito.ArgumentCaptor<Payment> saved = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getPaymentType()).isEqualTo("REFUND");
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("10000000");
        assertThat(response.getRefundPercentage()).isEqualTo(100);
        assertThat(response.getPenaltyAmount()).isZero();
        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(booking.getDetailsJson()).contains("\"cancelledBy\":\"COMPANY\"");
        verify(staffNotifier).bookingCancelled(org.mockito.ArgumentMatchers.eq(booking), any(), any());
        verify(staffNotifier).toCustomer(org.mockito.ArgumentMatchers.eq(booking), any(), org.mockito.ArgumentMatchers.contains("Hoàn lại toàn bộ"));
    }

    @Test
    void cancelTrip_byCustomerFault_withHorsesOnBoard_waitsForTheCrewToReturnThem() {
        booking.setStatus("en_route_to_pickup");
        when(bookingService.isPickupComplete(booking)).thenReturn(true);

        var response = service.cancelTrip(CODE, "CUSTOMER", "Khách không thanh toán 70%", "manager01");

        assertThat(booking.getStatus()).isEqualTo("returning_horses");
        assertThat(response.getBookingStatus()).isEqualTo("returning_horses");
        assertThat(response.getRefundAmount()).isZero();
        assertThat(response.getPenaltyAmount()).isEqualByComparingTo("3000000");
        assertThat(response.getMessage()).contains("trả lại ngựa và hồ sơ gốc");
        verify(staffNotifier).horsesToReturn(org.mockito.ArgumentMatchers.eq(booking), any(),
                org.mockito.ArgumentMatchers.eq("Khách không thanh toán 70%"));
        verify(staffNotifier, never()).bookingCancelled(any(), any(), any());
    }

    @Test
    void cancelTrip_byCustomerFault_afterBalancePaid_refundsNothing() {
        booking.setStatus("en_route_to_pickup");
        when(bookingService.isPickupComplete(booking)).thenReturn(true);
        Payment deposit = Payment.builder().id(1).bookingID(booking).amount(new BigDecimal("3000000"))
                .paymentType("DEPOSIT").status("COMPLETED").build();
        Payment balance = Payment.builder().id(2).bookingID(booking).amount(new BigDecimal("7000000"))
                .paymentType("BALANCE").status("COMPLETED").build();
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(42)).thenReturn(List.of(balance, deposit));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.cancelTrip(CODE, "CUSTOMER", "Khách đổi ý sau khi giao ngựa", "manager01");

        org.mockito.ArgumentCaptor<Payment> saved = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getPaymentType()).isEqualTo("PENALTY");
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("10000000");
        assertThat(response.getRefundAmount()).isZero();
        assertThat(response.getMessage()).contains("không hoàn tiền");
        assertThat(booking.getStatus()).isEqualTo("returning_horses");
    }

    @Test
    void cancelTrip_byCompany_withHorsesOnBoard_refundsAndWaitsForTheReturn() {
        booking.setStatus("en_route_to_pickup");
        when(bookingService.isPickupComplete(booking)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.cancelTrip(CODE, "COMPANY", "Xe hỏng điều hòa", "manager01");

        assertThat(response.getRefundAmount()).isEqualByComparingTo("3000000");
        assertThat(booking.getStatus()).isEqualTo("returning_horses");
        verify(staffNotifier).horsesToReturn(org.mockito.ArgumentMatchers.eq(booking), any(), any());
    }

    @Test
    void cancelTrip_isRejectedAfterDepartureWithoutReasonOrUnknownFault() {
        booking.setStatus("in_transit");
        assertThatThrownBy(() -> service.cancelTrip(CODE, "COMPANY", "x", "manager01")).isInstanceOf(IllegalStateException.class);
        booking.setStatus("waybill_issued");
        assertThatThrownBy(() -> service.cancelTrip(CODE, "COMPANY", " ", "manager01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.cancelTrip(CODE, "OTHER", "x", "manager01")).isInstanceOf(IllegalArgumentException.class);
        assertThat(booking.getStatus()).isEqualTo("waybill_issued");
    }

    @Test
    void paymentViews_forAnotherCustomer_areDenied() {
        assertThatThrownBy(() -> service.getPaymentSummary(CODE, "khac")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getPaymentsByBooking(CODE, "khac")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getPaymentDetail(1, "khac")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void paymentViews_forOwnerOrStaff_areAllowed() {
        assertThat(service.getPaymentSummary(CODE, "phuc@example.com").getBookingCode()).isEqualTo(CODE);
        assertThat(service.getPaymentsByBooking(CODE, null)).hasSize(1);
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

        assertThatThrownBy(() -> service.cancelWithRefundPolicy(CODE, CancelBookingRequestDto.builder().reason("Đổi lịch").build(), "phuc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hotline");
        assertThat(booking.getStatus()).isEqualTo("ready_for_pickup");
        verify(bookingRepository, never()).save(any());
    }
}
