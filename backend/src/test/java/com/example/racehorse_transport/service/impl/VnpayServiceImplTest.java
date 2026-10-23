package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.dto.payment.VnpayCreateRequest;
import com.example.racehorse_transport.dto.payment.VnpayCreateResponse;
import com.example.racehorse_transport.dto.payment.VnpayResultResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.SettlementService;
import com.example.racehorse_transport.dto.settlement.RatingDto;
import com.example.racehorse_transport.util.VnpayUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnpayServiceImplTest {

    private static final String SECRET = "TESTSECRET";

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final QuotationRepository quotationRepository = mock(QuotationRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final SettlementService settlementService = mock(SettlementService.class);
    private VnpayServiceImpl service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        VnpayProperties props = new VnpayProperties();
        ReflectionTestUtils.setField(props, "tmnCode", "TESTTMN");
        ReflectionTestUtils.setField(props, "hashSecret", SECRET);
        ReflectionTestUtils.setField(props, "payUrl", "https://pay.test/vpcpay.html");
        ReflectionTestUtils.setField(props, "returnUrl", "http://localhost:5173/payment/vnpay-return");
        ReflectionTestUtils.setField(props, "expireMinutes", 15);
        service = new VnpayServiceImpl(props, bookingRepository, quotationRepository, paymentRepository, bookingService, settlementService);

        User user = new User();
        user.setUsername("khach1");
        Customer customer = new Customer();
        customer.setUser(user);
        booking = Booking.builder().id(12).status("awaiting_payment").customerID(customer).build();
        when(bookingRepository.findById(12)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(anyInt())).thenReturn(List.of());
    }

    private Quotation quotation(Instant expiresAt) {
        return Quotation.builder()
                .depositAmount(new BigDecimal("3000000.00"))
                .balanceAmount(new BigDecimal("7000000.00"))
                .totalAmount(new BigDecimal("10000000.00"))
                .expiresAt(expiresAt)
                .build();
    }

    private Payment pendingDeposit() {
        return Payment.builder().id(1).bookingID(booking).amount(new BigDecimal("3000000"))
                .paymentType("DEPOSIT").paymentMethod("VNPAY").transactionCode("DEP00121").status("PENDING").build();
    }

    private Map<String, String> signedCallback(String amount, String responseCode) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "DEP00121");
        params.put("vnp_Amount", amount);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", responseCode);
        params.put("vnp_TransactionNo", "14512345");
        params.put("vnp_BankCode", "NCB");
        params.put(VnpayUtil.SECURE_HASH, VnpayUtil.sign(params, SECRET));
        return params;
    }

    @Test
    void createDeposit_savesPendingPaymentAndSignsServerSideAmount() {
        when(quotationRepository.findTopByBookingIdOrderBySentAtDesc(12))
                .thenReturn(Optional.of(quotation(Instant.now().plus(10, ChronoUnit.HOURS))));

        VnpayCreateResponse res = service.createPaymentUrl(
                VnpayCreateRequest.builder().bookingId("BK-12").paymentType("deposit").build(), "khach1", "1.2.3.4");

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(saved.getValue().getPaymentMethod()).isEqualTo("VNPAY");
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("3000000");
        assertThat(res.getTransactionCode()).isEqualTo(saved.getValue().getTransactionCode()).startsWith("DEP0012");

        Map<String, String> query = new HashMap<>();
        for (String pair : res.getPaymentUrl().substring(res.getPaymentUrl().indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.US_ASCII));
        }
        assertThat(query.get("vnp_Amount")).isEqualTo("300000000");
        assertThat(query.get("vnp_TmnCode")).isEqualTo("TESTTMN");
        assertThat(query.get("vnp_IpAddr")).isEqualTo("1.2.3.4");
        assertThat(VnpayUtil.verify(query, SECRET)).isTrue();
    }

    @Test
    void createDeposit_linkNeverOutlivesQuotation() {
        Instant quoteEnds = Instant.now().plus(5, ChronoUnit.MINUTES);
        when(quotationRepository.findTopByBookingIdOrderBySentAtDesc(12)).thenReturn(Optional.of(quotation(quoteEnds)));

        VnpayCreateResponse res = service.createPaymentUrl(
                VnpayCreateRequest.builder().bookingId("12").paymentType("DEPOSIT").build(), "khach1", "1.2.3.4");

        assertThat(res.getExpiresAt()).isEqualTo(quoteEnds.toEpochMilli());
    }

    @Test
    void create_rejectsOtherCustomerAndExpiredQuotation() {
        assertThatThrownBy(() -> service.createPaymentUrl(
                VnpayCreateRequest.builder().bookingId("12").paymentType("DEPOSIT").build(), "nguoi_khac", "1.2.3.4"))
                .isInstanceOf(AccessDeniedException.class);

        when(quotationRepository.findTopByBookingIdOrderBySentAtDesc(12))
                .thenReturn(Optional.of(quotation(Instant.now().minus(1, ChronoUnit.MINUTES))));
        assertThatThrownBy(() -> service.createPaymentUrl(
                VnpayCreateRequest.builder().bookingId("12").paymentType("DEPOSIT").build(), "khach1", "1.2.3.4"))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void confirm_successAppliesDepositOnce() {
        Payment payment = pendingDeposit();
        when(paymentRepository.findByTransactionCode("DEP00121")).thenReturn(Optional.of(payment));

        VnpayResultResponse first = service.confirm(signedCallback("300000000", "00"));
        VnpayResultResponse second = service.confirm(signedCallback("300000000", "00"));

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getRspCode()).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo("COMPLETED");
        assertThat(payment.getGatewayTransactionNo()).isEqualTo("14512345");
        assertThat(second.getRspCode()).isEqualTo("02");
        assertThat(second.isSuccess()).isTrue();
        verify(bookingService, times(1)).confirmDepositPaid(payment, "khach1");
    }

    @Test
    void confirm_rejectsBadSignatureAndWrongAmount() {
        Payment payment = pendingDeposit();
        when(paymentRepository.findByTransactionCode("DEP00121")).thenReturn(Optional.of(payment));

        Map<String, String> forged = signedCallback("300000000", "00");
        forged.put("vnp_ResponseCode", "00");
        forged.put("vnp_Amount", "100");
        assertThat(service.confirm(forged).getRspCode()).isEqualTo("97");

        assertThat(service.confirm(signedCallback("100", "00")).getRspCode()).isEqualTo("04");
        assertThat(payment.getStatus()).isEqualTo("PENDING");
        verify(bookingService, never()).confirmDepositPaid(any(), anyString());
    }

    @Test
    void confirm_cancelledByCustomerMarksFailed() {
        Payment payment = pendingDeposit();
        when(paymentRepository.findByTransactionCode("DEP00121")).thenReturn(Optional.of(payment));

        VnpayResultResponse res = service.confirm(signedCallback("300000000", "24"));

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getRspCode()).isEqualTo("00");
        assertThat(payment.getStatus()).isEqualTo("FAILED");
        verify(bookingService, never()).confirmDepositPaid(any(), anyString());
    }

    @Test
    void confirm_paidAfterQuoteExpiredNeedsReview() {
        booking.setStatus("quote_expired");
        Payment payment = pendingDeposit();
        when(paymentRepository.findByTransactionCode("DEP00121")).thenReturn(Optional.of(payment));

        VnpayResultResponse res = service.confirm(signedCallback("300000000", "00"));

        assertThat(res.isSuccess()).isFalse();
        assertThat(payment.getStatus()).isEqualTo("NEEDS_REVIEW");
        verify(bookingService, never()).confirmDepositPaid(any(), anyString());
    }

    @Test
    void settlement_holdsRatingThenClosesOrderOnlyAfterVnpayConfirms() {
        booking.setStatus("settlement_issued");
        RatingDto rating = RatingDto.builder().stars(5).comment("Tốt").build();
        when(settlementService.holdForVnpay(booking, rating)).thenReturn(new BigDecimal("1250000"));

        VnpayCreateResponse res = service.createPaymentUrl(
                VnpayCreateRequest.builder().bookingId("BK-012").paymentType("SETTLEMENT").rating(rating).build(), "khach1", "1.2.3.4");

        assertThat(res.getAmount()).isEqualByComparingTo("1250000");
        assertThat(res.getTransactionCode()).startsWith("SET0012");
        verify(settlementService, never()).confirmSettlementPaid(any(), anyString());

        Payment payment = Payment.builder().id(2).bookingID(booking).amount(new BigDecimal("1250000"))
                .paymentType("SETTLEMENT").paymentMethod("VNPAY").transactionCode("DEP00121").status("PENDING").build();
        when(paymentRepository.findByTransactionCode("DEP00121")).thenReturn(Optional.of(payment));

        assertThat(service.confirm(signedCallback("125000000", "00")).isSuccess()).isTrue();
        verify(settlementService, times(1)).confirmSettlementPaid(payment, "khach1");
    }

    private Payment completedDeposit() {
        return Payment.builder().id(2).bookingID(booking).amount(new BigDecimal("3000000"))
                .paymentType("DEPOSIT").paymentMethod("VNPAY").status("COMPLETED").build();
    }

    @Test
    void createBalance_beforeTheVehicleIsReady_isRejected() {
        booking.setStatus("clearance_in_progress");
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(anyInt())).thenReturn(List.of(completedDeposit()));

        assertThatThrownBy(() -> service.createPaymentUrl(
                VnpayCreateRequest.builder().bookingId("12").paymentType("BALANCE").build(), "khach1", "1.2.3.4"))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createBalance_onceTheVehicleIsReady_savesPendingPayment() {
        booking.setStatus("ready_for_pickup");
        when(paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(anyInt())).thenReturn(List.of(completedDeposit()));
        when(quotationRepository.findTopByBookingIdOrderBySentAtDesc(12))
                .thenReturn(Optional.of(quotation(Instant.now().plus(10, ChronoUnit.HOURS))));

        service.createPaymentUrl(VnpayCreateRequest.builder().bookingId("12").paymentType("BALANCE").build(), "khach1", "1.2.3.4");

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("7000000");
    }
}
