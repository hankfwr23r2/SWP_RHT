package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.client.MailClient;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.PaymentTransactionRepository;
import com.example.racehorse_transport.repository.QuotationLineRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BillMailServiceImplTest {

    private final MailClient mail = mock(MailClient.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final QuotationRepository quotations = mock(QuotationRepository.class);
    private final QuotationLineRepository lines = mock(QuotationLineRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private BillMailServiceImpl service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new BillMailServiceImpl(mail, bookings, quotations, lines, payments, transactions, transactionManager,
                "http://localhost:5173/");
        user = new AppUser();
        user.setEmail("an@gmail.com");
        user.setPreferredLanguage("vi");
        Customer customer = new Customer();
        customer.setFullName("An <Nguyễn>");
        customer.setUser(user);
        Booking booking = new Booking();
        booking.setId(7L);
        booking.setCode("RH-0007");
        booking.setCustomer(customer);
        when(bookings.findById(7L)).thenReturn(Optional.of(booking));
        when(payments.findByBooking_IdOrderById(7L)).thenReturn(List.of());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    private static Quotation quotation(QuotationStatus status) {
        Quotation quotation = new Quotation();
        quotation.setId(70L);
        quotation.setStatus(status);
        quotation.setTotalAmount(new BigDecimal("10200000"));
        quotation.setDepositAmount(new BigDecimal("3260000"));
        quotation.setExpiresAt(Instant.parse("2026-12-01T03:00:00Z"));
        return quotation;
    }

    private static QuotationLine line(String description, long amount, boolean passThrough) {
        QuotationLine line = new QuotationLine();
        line.setDescription(description);
        line.setAmount(BigDecimal.valueOf(amount));
        line.setPassThrough(passThrough);
        return line;
    }

    private static Payment payment(PaymentType type, PaymentStatus status, long amount) {
        Payment payment = new Payment();
        payment.setId(amount);
        payment.setPaymentType(type);
        payment.setStatus(status);
        payment.setAmount(BigDecimal.valueOf(amount));
        payment.setPaidAt(status == PaymentStatus.PAID ? Instant.parse("2026-11-28T02:00:00Z") : null);
        payment.setDueAt(Instant.parse("2026-12-05T02:00:00Z"));
        return payment;
    }

    private String sentHtml(String subject) {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(mail).sendHtml(eq("an@gmail.com"), eq(subject), html.capture(), anyString());
        return html.getValue();
    }

    @Test
    void mailsAreSentOnlyAtMoneyMilestones() {
        assertThat(BillMailServiceImpl.kindOf(BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT)).isEqualTo(BillMailServiceImpl.Kind.QUOTE);
        assertThat(BillMailServiceImpl.kindOf(BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED))
                .isEqualTo(BillMailServiceImpl.Kind.DEPOSIT_RECEIPT);
        // Chọn lại chuyến xong quay về đã xác nhận: không gửi lại biên nhận cọc
        assertThat(BillMailServiceImpl.kindOf(BookingStatus.SCHEDULE_CHANGE_REQUIRED, BookingStatus.CONFIRMED)).isNull();
        assertThat(BillMailServiceImpl.kindOf(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION)).isNull();
        assertThat(BillMailServiceImpl.kindOf(BookingStatus.DELIVERED, BookingStatus.COMPLETED)).isEqualTo(BillMailServiceImpl.Kind.STATEMENT);
    }

    @Test
    void quotationMailListsLinesTotalsAndEscapesNames() {
        when(quotations.findByBooking_IdAndStatus(7L, QuotationStatus.SENT)).thenReturn(Optional.of(quotation(QuotationStatus.SENT)));
        when(lines.findByQuotation_IdOrderByLineNumber(70L)).thenReturn(List.of(
                line("Cước trục TP.HCM – Đà Nẵng", 10_000_000, false), line("Lệ phí nhà nước", 200_000, true)));

        service.statusChanged(7L, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, null);

        String html = sentHtml("Báo giá đơn RH-0007");
        assertThat(html).contains("10,200,000 ₫", "3,260,000 ₫", "6,940,000 ₫", "Lệ phí nhà nước (thu hộ)",
                "01/12/2026 10:00", "http://localhost:5173/orders/7", "An &lt;Nguyễn&gt;");
        assertThat(html).doesNotContain("<Nguyễn>");
    }

    @Test
    void englishCustomersGetEnglishMail() {
        user.setPreferredLanguage("en");
        when(quotations.findByBooking_IdAndStatus(7L, QuotationStatus.SENT)).thenReturn(Optional.of(quotation(QuotationStatus.SENT)));
        when(lines.findByQuotation_IdOrderByLineNumber(70L)).thenReturn(List.of(line("Linehaul", 10_200_000, false)));

        service.statusChanged(7L, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, null);

        assertThat(sentHtml("Quotation for order RH-0007")).contains("10,200,000 VND", "Remaining balance");
    }

    @Test
    void cancellationShowsRefundAndWhatTheCompanyKeeps() {
        when(quotations.findByBooking_IdAndStatus(7L, QuotationStatus.ACCEPTED)).thenReturn(Optional.of(quotation(QuotationStatus.ACCEPTED)));
        when(payments.findByBooking_IdOrderById(7L)).thenReturn(List.of(
                payment(PaymentType.DEPOSIT, PaymentStatus.PAID, 3_260_000), payment(PaymentType.REFUND, PaymentStatus.PENDING, 200_000)));

        service.statusChanged(7L, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, "Khách đổi kế hoạch");

        assertThat(sentHtml("Đã hủy đơn RH-0007"))
                .contains("3,260,000 ₫", "200,000 ₫", "3,060,000 ₫", "Lý do: Khách đổi kế hoạch", "05/12/2026 09:00");
    }

    @Test
    void finalStatementListsPaymentsAndNothingOutstanding() {
        when(quotations.findByBooking_IdAndStatus(7L, QuotationStatus.ACCEPTED)).thenReturn(Optional.of(quotation(QuotationStatus.ACCEPTED)));
        when(payments.findByBooking_IdOrderById(7L)).thenReturn(List.of(
                payment(PaymentType.DEPOSIT, PaymentStatus.PAID, 3_260_000), payment(PaymentType.BALANCE, PaymentStatus.PAID, 6_940_000)));

        service.statusChanged(7L, BookingStatus.DELIVERED, BookingStatus.COMPLETED, null);

        assertThat(sentHtml("Bảng quyết toán · đơn RH-0007"))
                .contains("Tiền cọc · 28/11/2026 09:00", "Số dư · 28/11/2026 09:00", "Còn nợ", ">0 ₫<");
    }

    @Test
    void mailWaitsForCommitAndIsDroppedOnRollback() {
        when(quotations.findByBooking_IdAndStatus(7L, QuotationStatus.SENT)).thenReturn(Optional.of(quotation(QuotationStatus.SENT)));
        TransactionSynchronizationManager.initSynchronization();

        service.statusChanged(7L, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, null);
        verify(mail, never()).sendHtml(anyString(), anyString(), anyString(), anyString());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(mail).sendHtml(eq("an@gmail.com"), eq("Báo giá đơn RH-0007"), anyString(), anyString());
        TransactionSynchronizationManager.clearSynchronization();

        // Giao dịch bị hoàn tác: không có afterCommit nên không có mail mới
        TransactionSynchronizationManager.initSynchronization();
        service.statusChanged(7L, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, null);
        TransactionSynchronizationManager.clearSynchronization();
        verify(mail).sendHtml(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void mailFailureDoesNotBreakTheCaller() {
        when(quotations.findByBooking_IdAndStatus(7L, QuotationStatus.SENT)).thenReturn(Optional.of(quotation(QuotationStatus.SENT)));
        doThrow(new IllegalStateException("SMTP down")).when(mail).sendHtml(anyString(), anyString(), anyString(), anyString());

        service.statusChanged(7L, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, null);

        verify(mail).sendHtml(anyString(), anyString(), anyString(), anyString());
    }
}
