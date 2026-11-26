package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.payment.DebtDisputeDecisionRequestDto;
import com.example.racehorse_transport.dto.payment.DebtDisputeRequestDto;
import com.example.racehorse_transport.dto.payment.DebtExtensionRequestDto;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.DisputeDecision;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CustomerDebtPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.CustomerDebtService;
import com.example.racehorse_transport.service.DocumentService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CustomerDebtServiceImpl implements CustomerDebtService {
    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final AppUserRepository users;
    private final StaffRepository staff;
    private final DocumentService documents;
    private final NotificationService notifications;
    private final AuditService audit;
    private final SystemConfigService config;
    private final Clock clock;

    @Override
    @Transactional
    public PaymentResponseDto dispute(Long paymentId, DebtDisputeRequestDto request, String username) {
        Payment payment = lock(paymentId);
        if (!payment.getBooking().getCustomer().getUser().getUsername().equals(username)) {
            throw notFound();
        }
        CustomerDebtPolicy.requireDisputable(payment);
        payment.setDisputedAt(clock.instant());
        payment.setDisputeReason(request.reason().strip());
        toManagers(payment, "Khách khiếu nại khoản thu", "Đơn " + payment.getBooking().getCode() + " có khoản thu bị khiếu nại.");
        return PaymentResponseDto.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto decideDispute(Long paymentId, DebtDisputeDecisionRequestDto request, String username) {
        Payment payment = lock(paymentId);
        CustomerDebtPolicy.requireDisputePending(payment);
        Instant now = clock.instant();
        payment.setDisputeDecision(request.decision());
        payment.setDisputeResponse(request.response().strip());
        if (request.decision() == DisputeDecision.ACCEPTED) {
            payment.setStatus(PaymentStatus.CANCELLED);
        } else {
            payment.setDueAt(CustomerDebtPolicy.dueAfterDispute(payment, now));
        }
        payment.setDisputeResolvedAt(now);
        record(username, "DEBT_DISPUTE_DECIDED", payment, Map.of("decision", request.decision().name()));
        toCustomer(payment, "Đã kết luận khiếu nại khoản thu", request.decision() == DisputeDecision.ACCEPTED
                ? "Khoản thu đã được hủy." : "Khoản thu giữ nguyên, hạn trả đã lùi theo thời gian xem xét.");
        return PaymentResponseDto.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto extend(Long paymentId, DebtExtensionRequestDto request, String username) {
        Payment payment = lock(paymentId);
        CustomerDebtPolicy.requireExtendable(payment, request.days(), config.getInt("DEBT_EXTENSION_MAX_DAYS"));
        Instant now = clock.instant();
        Instant base = payment.getDueAt() == null || payment.getDueAt().isBefore(now) ? now : payment.getDueAt();
        payment.setDueAt(base.plus(Duration.ofDays(request.days())));
        payment.setExtendedAt(now);
        record(username, "DEBT_EXTENDED", payment, Map.of("days", request.days()));
        toCustomer(payment, "Đã gia hạn khoản thu", "Xem hạn mới trong đơn hàng.");
        return PaymentResponseDto.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto writeOff(Long paymentId, DebtDisputeRequestDto request, MultipartFile decision, String username) {
        Payment payment = lock(paymentId);
        Instant now = clock.instant();
        CustomerDebtPolicy.requireWriteOff(payment, now, config.getInt("DEBT_BAD_DAYS"));
        if (decision == null || decision.isEmpty()) {
            throw new BookingRuleException("Cần tệp quyết định xóa nợ.");
        }
        AppUser actor = actor(username);
        documents.attach(decision, "DEBT_DECISION", DocumentSubject.BOOKING, payment.getBooking().getId(), actor.getId());
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setWrittenOffAt(now);
        payment.setWrittenOffBy(actor);
        payment.setWriteOffReason(request.reason().strip());
        record(username, "DEBT_WRITTEN_OFF", payment, Map.of("reason", request.reason().strip()));
        return PaymentResponseDto.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto reopen(Long paymentId, String username) {
        Payment payment = lock(paymentId);
        CustomerDebtPolicy.requireWrittenOff(payment);
        payment.setWrittenOffAt(null);
        payment.setWrittenOffBy(null);
        payment.setWriteOffReason(null);
        payment.setBlockReleasedAt(null);
        payment.setStatus(PaymentStatus.PENDING);
        record(username, "DEBT_REOPENED", payment, Map.of("status", PaymentStatus.PENDING.name()));
        toCustomer(payment, "Khoản thu được mở lại", "Bạn có thể thanh toán khoản này trong đơn hàng.");
        return PaymentResponseDto.from(payment);
    }

    @Override
    @Transactional
    public PaymentResponseDto releaseBlock(Long paymentId, String username) {
        Payment payment = lock(paymentId);
        CustomerDebtPolicy.requireWrittenOff(payment);
        if (payment.getBlockReleasedAt() == null) {
            payment.setBlockReleasedAt(clock.instant());
            record(username, "DEBT_BLOCK_RELEASED", payment, Map.of("released", true));
        }
        return PaymentResponseDto.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> followUpCandidates() {
        return payments.findIssuedBefore(PaymentType.EXTRA_CHARGE, PaymentStatus.PENDING,
                clock.instant().minus(Duration.ofDays(config.getInt("DEBT_CALL_DAYS"))));
    }

    @Override
    @Transactional
    public void followUp(Long paymentId) {
        Payment payment = lock(paymentId);
        Instant now = clock.instant();
        if (!CustomerDebtPolicy.isOverdue(payment, now)) return;
        Long bookingId = payment.getBooking().getId();
        String code = payment.getBooking().getCode();
        if (CustomerDebtPolicy.isBad(payment, now, config.getInt("DEBT_BAD_DAYS"))) {
            String title = "Nợ khó thu · khoản " + paymentId;
            if (!bookings.hasNotificationBetween(bookingId, title, payment.getIssuedAt(), now.plusSeconds(60))) {
                toCustomer(payment, title, "Khoản thu của đơn " + code + " đã quá hạn lâu. Vui lòng thanh toán hoặc liên hệ công ty.");
                toManagers(payment, "Nợ khó thu cần quyết định · khoản " + paymentId, "Đơn " + code + ": tiếp tục đòi hoặc xóa nợ.");
            }
            return;
        }
        String title = "Cần gọi khách nợ · khoản " + paymentId;
        if (!bookings.hasNotificationBetween(bookingId, title, payment.getIssuedAt(), now.plusSeconds(60))) {
            toManagers(payment, title, "Đơn " + code + " có khoản thu quá hạn. Gọi khách, có thể gia hạn một lần.");
        }
    }

    private Payment lock(Long paymentId) {
        Long bookingId = payments.findBookingId(paymentId).orElseThrow(CustomerDebtServiceImpl::notFound);
        bookings.findForUpdate(bookingId).orElseThrow(CustomerDebtServiceImpl::notFound);
        return payments.findById(paymentId).filter(row -> row.getPaymentType() == PaymentType.EXTRA_CHARGE)
                .orElseThrow(CustomerDebtServiceImpl::notFound);
    }

    private AppUser actor(String username) {
        return users.findByUsername(username).orElseThrow(CustomerDebtServiceImpl::notFound);
    }

    private void record(String username, String action, Payment payment, Map<String, ?> data) {
        audit.record(actor(username).getId(), action, "payment", payment.getId(), null, data);
    }

    private void toCustomer(Payment payment, String title, String message) {
        var booking = payment.getBooking();
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), title, message,
                "/orders/" + booking.getId());
    }

    private void toManagers(Payment payment, String title, String message) {
        var ids = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
        if (!ids.isEmpty()) {
            notifications.notify(ids, payment.getBooking().getId(), title, message,
                    "/manager/orders/" + payment.getBooking().getId());
        }
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy khoản thu.");
    }
}
