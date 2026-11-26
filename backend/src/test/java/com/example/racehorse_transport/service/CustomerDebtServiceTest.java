package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.dto.payment.DebtDisputeDecisionRequestDto;
import com.example.racehorse_transport.dto.payment.DebtDisputeRequestDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.DisputeDecision;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.impl.CustomerDebtServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CustomerDebtServiceTest {
    private static final Instant NOW = Instant.parse("2026-12-31T01:00:00Z");
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final DocumentService documents = mock(DocumentService.class);
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final CustomerDebtService service = new CustomerDebtServiceImpl(payments, bookings, users, mock(StaffRepository.class),
            documents, mock(NotificationService.class), mock(AuditService.class), config, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Payment payment = new Payment();

    @BeforeEach
    void pendingExtraCharge() {
        var owner = new AppUser();
        owner.setId(7L);
        owner.setUsername("owner");
        var customer = new Customer();
        customer.setUser(owner);
        var booking = new Booking();
        booking.setId(1L);
        booking.setCustomer(customer);
        payment.setId(5L);
        payment.setBooking(booking);
        payment.setPaymentType(PaymentType.EXTRA_CHARGE);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("500000"));
        payment.setIssuedAt(NOW.minus(Duration.ofDays(31)));
        payment.setDueAt(NOW.minus(Duration.ofDays(24)));
        when(payments.findBookingId(5L)).thenReturn(Optional.of(1L));
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        when(payments.findById(5L)).thenReturn(Optional.of(payment));
        var manager = new AppUser();
        manager.setId(9L);
        when(users.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(config.getInt("DEBT_BAD_DAYS")).thenReturn(30);
    }

    @Test
    void onlyTheOwnerDisputesAndOnlyOnce() {
        var request = new DebtDisputeRequestDto("Không đúng");
        assertThatThrownBy(() -> service.dispute(5L, request, "other")).isInstanceOf(ResourceNotFoundException.class);
        service.dispute(5L, request, "owner");
        assertThatThrownBy(() -> service.dispute(5L, request, "owner")).isInstanceOf(BookingRuleException.class);
    }

    @Test
    void acceptedDisputeCancelsTheChargeAndRejectedOneKeepsIt() {
        payment.setDisputedAt(NOW.minus(Duration.ofDays(2)));
        payment.setDisputeReason("Không đúng");
        var due = payment.getDueAt();
        service.decideDispute(5L, new DebtDisputeDecisionRequestDto(DisputeDecision.REJECTED, "Đúng biên bản"), "manager");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getDueAt()).isEqualTo(due.plus(Duration.ofDays(2)));
        assertThatThrownBy(() -> service.decideDispute(5L, new DebtDisputeDecisionRequestDto(DisputeDecision.ACCEPTED, "x"), "manager"))
                .isInstanceOf(BookingRuleException.class);

        payment.setDisputeResolvedAt(null);
        payment.setDisputeDecision(null);
        service.decideDispute(5L, new DebtDisputeDecisionRequestDto(DisputeDecision.ACCEPTED, "Nhầm"), "manager");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void writeOffNeedsAFileAndCanBeReopened() {
        var request = new DebtDisputeRequestDto("Không liên lạc được");
        assertThatThrownBy(() -> service.writeOff(5L, request, null, "manager")).isInstanceOf(BookingRuleException.class);
        verifyNoInteractions(documents);
        var file = new MockMultipartFile("decision", "qd.pdf", "application/pdf", new byte[] {1});
        service.writeOff(5L, request, file, "manager");
        verify(documents).attach(file, "DEBT_DECISION", DocumentSubject.BOOKING, 1L, 9L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThatThrownBy(() -> service.writeOff(5L, request, file, "manager")).isInstanceOf(BookingRuleException.class);
        service.releaseBlock(5L, "manager");
        assertThat(payment.getBlockReleasedAt()).isEqualTo(NOW);
        service.reopen(5L, "manager");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getWrittenOffAt()).isNull();
    }
}
