package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import com.example.racehorse_transport.dto.booking.CancelBookingRequestDto;
import com.example.racehorse_transport.dto.booking.CompanyCancelBookingRequestDto;
import com.example.racehorse_transport.dto.booking.ManagerCancelBookingRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingCancellationPolicy;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.Breakdown;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.Outcome;
import com.example.racehorse_transport.policy.PaymentPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.BookingCancellationService;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.RefundService;
import java.math.BigDecimal;
import com.example.racehorse_transport.service.PaymentCheckoutService;
import com.example.racehorse_transport.service.ScheduleChangeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingCancellationServiceImpl implements BookingCancellationService {
    private final BookingRepository bookings;
    private final BookingHorseRepository horses;
    private final QuotationRepository quotations;
    private final PaymentRepository payments;
    private final BookingStatusService statuses;
    private final DepartureService departures;
    private final RefundService refunds;
    private final PaymentCheckoutService checkout;
    private final ScheduleChangeService schedules;

    @Override
    @Transactional(readOnly = true)
    public BookingCancellationResponseDto preview(Long bookingId, String username) {
        var booking = bookings.findByIdAndCustomer_User_Username(bookingId, username)
                .orElseThrow(BookingCancellationServiceImpl::notFound);
        return evaluate(booking, false, false).response(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingCancellationResponseDto previewForManager(Long bookingId, boolean byCompany) {
        var booking = bookings.findById(bookingId).orElseThrow(BookingCancellationServiceImpl::notFound);
        return evaluate(booking, byCompany, !byCompany).response(booking);
    }

    @Override
    @Transactional
    public BookingCancellationResponseDto cancelByCustomer(Long bookingId, String username, CancelBookingRequestDto request) {
        var booking = lock(bookingId);
        if (!username.equals(booking.getCustomer().getUser().getUsername())) {
            throw notFound();
        }
        return cancel(booking, evaluate(booking, false, false), request.reason(), null,
                booking.getCustomer().getUser().getId(), List.of());
    }

    @Override
    @Transactional
    public BookingCancellationResponseDto cancelForCompany(Long bookingId, String username, CompanyCancelBookingRequestDto request) {
        var booking = lock(bookingId);
        return cancel(booking, evaluate(booking, true, false), request.reason(), username, null, List.of());
    }

    @Override
    @Transactional
    public BookingCancellationResponseDto cancelForCustomer(Long bookingId, String username, ManagerCancelBookingRequestDto request) {
        var booking = lock(bookingId);
        return cancel(booking, evaluate(booking, false, true), request.reason(), username, null, request.unspentQuotationLineIds());
    }

    @Override
    @Transactional
    public void cancelUnpaidAtPickup(Long bookingId, Long actorUserId) {
        var booking = lock(bookingId);
        var cancellation = evaluate(booking, false, true);
        if (actorUserId == null || booking.getStatus() != BookingStatus.CONFIRMED || !checkout.isBalanceOverdue(cancellation.payments())) {
            throw BookingRuleException.conflict("Đơn chưa đủ điều kiện hủy do quá thời gian chờ trả số dư.");
        }
        cancel(booking, cancellation, "Quá thời gian chờ thanh toán số dư tại nơi nhận ngựa.", null, actorUserId, List.of());
    }

    private Cancellation evaluate(Booking booking, boolean company, boolean allowPickupCancellation) {
        Long bookingId = booking.getId();
        var entries = payments.findByBooking_IdOrderById(bookingId);
        var quotation = quotations.findByBooking_IdAndStatus(bookingId, QuotationStatus.ACCEPTED).orElse(null);
        boolean depositPaid = entries.stream()
                .anyMatch(row -> row.getPaymentType() == PaymentType.DEPOSIT && row.getStatus() == PaymentStatus.PAID);
        boolean paidInFull = depositPaid && quotation != null && PaymentPolicy.outstanding(quotation, entries).signum() <= 0;
        boolean received = entries.stream().anyMatch(row -> row.getPaymentType() == PaymentType.BALANCE)
                || bookings.hasReceivedHorses(bookingId);
        Outcome outcome = company
                ? BookingCancellationPolicy.companyCanCancel(booking.getStatus()) ? Outcome.FREE : Outcome.NOT_ALLOWED
                : BookingCancellationPolicy.byCustomer(booking.getStatus(), depositPaid, paidInFull);
        boolean refundTransport = false;
        // Công ty hủy thì luôn hoàn 100%, không xét nguyên nhân lỡ chuyến
        if (!company && booking.getStatus() == BookingStatus.SCHEDULE_CHANGE_REQUIRED && depositPaid) {
            var terms = schedules.cancellationTerms(bookingId);
            outcome = terms.outcome();
            refundTransport = terms.refundTransportDeposit();
        }
        // Link bỏ dở không chặn hủy đơn; tiền về sau khi hủy được ghi nhận để quản lý đối soát
        boolean awaitingVnpay = received && allowPickupCancellation && checkout.hasPendingBalanceConfirmation(bookingId);
        if ((received && (!allowPickupCancellation || !checkout.isBalanceOverdue(entries)))
                || awaitingVnpay || booking.getStatus() == BookingStatus.CONFIRMED && !depositPaid && !company) {
            outcome = Outcome.NOT_ALLOWED;
        }
        var lines = quotation == null || outcome != Outcome.FORFEIT_DEPOSIT
                ? List.<QuotationLine>of() : quotations.findLinesForCancellation(quotation.getId());
        var amounts = outcome == Outcome.NOT_ALLOWED
                ? BookingCancellationPolicy.refundAll(BigDecimal.ZERO)
                : BookingCancellationPolicy.amounts(outcome, refundTransport, quotation, lines, entries);
        return new Cancellation(outcome, quotation, entries, amounts);
    }

    private BookingCancellationResponseDto cancel(Booking booking, Cancellation cancellation, String reason,
            String username, Long actorUserId, List<Long> selectedLines) {
        if (reason == null || reason.isBlank() || reason.strip().length() > 500) {
            throw new BookingRuleException("Lý do hủy bắt buộc và tối đa 500 ký tự.");
        }
        if (cancellation.outcome() == Outcome.NOT_ALLOWED) {
            throw BookingRuleException.conflict("Đơn hiện chưa thể hủy. Kiểm tra trạng thái và giao dịch VNPAY.");
        }
        BookingCancellationPolicy.requireNoLineRefund(selectedLines);
        Long bookingId = booking.getId();
        if (booking.getDeparture() != null) {
            var active = horses.findForDetail(bookingId).stream().filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE)
                    .map(horse -> horse.getStallType().getCode()).toList();
            if (!active.isEmpty()) {
                departures.release(booking.getDeparture().getId(), StallSeatPolicy.total(active));
            }
            booking.setDeparture(null);
        }
        cancellation.payments().stream()
                .filter(row -> row.getStatus() == PaymentStatus.PENDING && row.getPaymentType() != PaymentType.REFUND)
                .forEach(row -> row.setStatus(PaymentStatus.CANCELLED));
        if (cancellation.amounts().refundable().signum() > 0) {
            refunds.createPending(bookingId, cancellation.quotation().getCurrencyCode(), cancellation.amounts().refundable());
        }
        if (username == null) {
            statuses.transition(bookingId, BookingStatus.CANCELLED, reason, actorUserId);
        } else {
            statuses.transitionForUser(bookingId, BookingStatus.CANCELLED, reason, username);
        }
        return cancellation.response(booking);
    }

    private Booking lock(Long id) {
        return bookings.findForUpdate(id).orElseThrow(BookingCancellationServiceImpl::notFound);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn.");
    }

    private record Cancellation(Outcome outcome, Quotation quotation, List<Payment> payments, Breakdown amounts) {
        BookingCancellationResponseDto response(Booking booking) {
            return BookingCancellationResponseDto.from(booking, outcome != Outcome.NOT_ALLOWED,
                    quotation == null ? null : quotation.getCurrencyCode(), amounts.refundable(), amounts.reviewable(),
                    amounts.forfeited(), List.of());
        }
    }
}
