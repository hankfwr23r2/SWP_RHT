package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.quotation.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.QuotationLineType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.QuotationPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.QuotationLineRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuotationServiceImpl implements QuotationService {
    private final BookingRepository bookings;
    private final QuotationRepository quotations;
    private final QuotationLineRepository lines;
    private final QuotationDraftService drafts;
    private final PaymentService payments;
    private final BookingStatusService statuses;
    private final StaffRepository staff;
    private final NotificationService notifications;
    private final SystemConfigService configs;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public QuotationResponseDto createDraft(Long bookingId) {
        return drafts.create(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotationResponseDto> quotations(Long bookingId, String username, boolean manager) {
        var booking = manager ? bookings.findById(bookingId) : bookings.findByIdAndCustomer_User_Username(bookingId, username);
        booking.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        return quotations.findByBooking_IdOrderByVersion(bookingId).stream()
                .filter(q -> manager || q.getStatus() != QuotationStatus.DRAFT)
                .map(this::describe).toList();
    }

    @Override
    @Transactional
    public QuotationResponseDto adjust(Long id, QuotationAdjustmentRequestDto request) {
        var booking = lockBooking(id, null);
        var quotation = getQuotation(id);
        requireDraft(booking, quotation);
        requireLatest(booking, quotation);

        var existing = new ArrayList<>(lines.findByQuotation_IdOrderByLineNumber(id));
        var line = request.toEntity(quotation);
        line.setLineNumber(existing.size() + 1);
        line.setLineType(QuotationLineType.ADJUSTMENT);
        line.setQuantity(BigDecimal.ONE);
        line.setUnit("PER_BOOKING");
        line.setUnitPrice(request.adjustment());
        line.setPassThrough(false);
        existing.add(line);
        drafts.setTotals(quotation, existing);
        lines.saveAndFlush(line);
        return QuotationResponseDto.from(quotation, existing);
    }

    @Override
    @Transactional
    public QuotationResponseDto send(Long id, String username) {
        var booking = lockBooking(id, null);
        var quotation = getQuotation(id);
        requireDraft(booking, quotation);
        requireLatest(booking, quotation);
        if (quotation.getDepositAmount().signum() == 0 || quotation.getTotalAmount().signum() == 0) {
            throw BookingRuleException.conflict("Tổng báo giá và tiền cọc phải lớn hơn 0 để gửi.");
        }
        if (!QuotationPolicy.matchesSchedule(quotation, booking)) {
            throw BookingRuleException.conflict("Lịch của đơn đã thay đổi. Vui lòng lập lại bản nháp.");
        }
        var now = clock.instant();
        var insuranceDeadlines = drafts.validateInsurance(quotation, now);
        var expiry = QuotationPolicy.deadline(now, configs.getInt("QUOTATION_VALID_HOURS"),
                quotation.getConfirmedPickupDate(), insuranceDeadlines);
        if (!expiry.isAfter(now)) {
            throw BookingRuleException.conflict("Báo giá đã qua hạn gửi.");
        }
        var manager = staff.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ quản lý."));
        quotations.findByBooking_IdOrderByVersion(booking.getId()).stream()
                .filter(q -> q.getVersion() < quotation.getVersion() && q.getSentAt() != null
                        && q.getStatus() != QuotationStatus.SUPERSEDED)
                .forEach(q -> changeStatus(q, QuotationStatus.SUPERSEDED));
        payments.cancelPendingDeposits(booking.getId());
        changeStatus(quotation, QuotationStatus.SENT);
        quotation.setSentBy(manager);
        quotation.setSentAt(now);
        quotation.setExpiresAt(expiry);
        statuses.transition(booking.getId(), BookingStatus.AWAITING_DEPOSIT, "Đã gửi báo giá.", manager.getUserId());
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), "Báo giá mới",
                "Báo giá đã sẵn sàng. Vui lòng xem và phản hồi trước khi hết hạn.", null);
        return describe(quotation);
    }

    @Override
    @Transactional
    public QuotationResponseDto accept(Long id, String username) {
        var booking = lockBooking(id, username);
        var quotation = getQuotation(id);
        var now = clock.instant();
        requireCurrent(booking, quotation, now);
        if (quotation.getStatus() == QuotationStatus.ACCEPTED) {
            return describe(quotation);
        }
        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw BookingRuleException.conflict("Báo giá chưa được gửi.");
        }
        changeStatus(quotation, QuotationStatus.ACCEPTED);
        quotation.setAcceptedAt(now);
        payments.createDeposit(quotation);
        var managers = staff.findUserIdsByRoleAndStatus("MANAGER",
                com.example.racehorse_transport.entity.enums.AppUserStatus.ACTIVE);
        if (!managers.isEmpty()) {
            notifications.notify(managers, booking.getId(), "Khách chấp nhận báo giá",
                    "Khách đã chấp nhận báo giá của đơn " + booking.getCode() + ".", null);
        }
        return describe(quotation);
    }

    @Override
    @Transactional
    public QuotationResponseDto reject(Long id, QuotationRejectRequestDto request, String username) {
        var booking = lockBooking(id, username);
        var quotation = getQuotation(id);
        requireCurrent(booking, quotation, clock.instant());
        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw BookingRuleException.conflict("Chỉ có thể từ chối báo giá đang chờ phản hồi.");
        }
        changeStatus(quotation, QuotationStatus.REJECTED);
        statuses.transition(booking.getId(), BookingStatus.QUOTING, request.reason().strip(),
                booking.getCustomer().getUser().getId());
        return describe(quotation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> expirationCandidates() {
        return quotations.findExpirableIds(BookingStatus.AWAITING_DEPOSIT,
                List.of(QuotationStatus.SENT, QuotationStatus.ACCEPTED), clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expire(Long id) {
        var booking = lockBooking(id, null);
        var quote = getQuotation(id);
        var previousDeparture = booking.getDeparture();
        var now = clock.instant();
        if (booking.getStatus() != BookingStatus.AWAITING_DEPOSIT
                || (quote.getStatus() != QuotationStatus.SENT && quote.getStatus() != QuotationStatus.ACCEPTED)
                || now.isBefore(quote.getExpiresAt())
                || !payments.expireDepositAndRelease(quote, now)) {
            return;
        }
        changeStatus(quote, QuotationStatus.EXPIRED);
        statuses.expireQuote(booking.getId(), previousDeparture);
    }

    private Booking lockBooking(Long quoteId, String customer) {
        return quotations.findBookingId(quoteId).flatMap(bookings::findForUpdate)
                .filter(row -> customer == null || customer.equals(row.getCustomer().getUser().getUsername()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo giá."));
    }

    private Quotation getQuotation(Long id) {
        return quotations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo giá."));
    }

    private static void changeStatus(Quotation quotation, QuotationStatus target) {
        QuotationPolicy.requireTransition(quotation.getStatus(), target);
        quotation.setStatus(target);
    }

    private QuotationResponseDto describe(Quotation quotation) {
        return QuotationResponseDto.from(quotation, lines.findByQuotation_IdOrderByLineNumber(quotation.getId()));
    }

    private void requireCurrent(Booking booking, Quotation quotation, java.time.Instant now) {
        if (booking.getStatus() != BookingStatus.AWAITING_DEPOSIT || !now.isBefore(quotation.getExpiresAt())) {
            throw BookingRuleException.conflict("Báo giá không còn chờ phản hồi hoặc đã hết hạn.");
        }
        requireLatest(booking, quotation);
    }

    private void requireLatest(Booking booking, Quotation quotation) {
        var latest = quotations.findByBooking_IdOrderByVersion(booking.getId()).getLast();
        if (!quotation.getId().equals(latest.getId())) {
            throw BookingRuleException.conflict("Đơn đã có phiên bản báo giá mới hơn.");
        }
    }

    private static void requireDraft(Booking booking, Quotation quotation) {
        if (booking.getStatus() != BookingStatus.QUOTING || quotation.getStatus() != QuotationStatus.DRAFT) {
            throw BookingRuleException.conflict("Chỉ thao tác trên bản nháp của đơn đang báo giá.");
        }
    }
}
