package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.payment.MarkRefundedRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.policy.BookingCancellationPolicy;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.Breakdown;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.Outcome;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.ScheduleChangeService;
import java.util.ArrayList;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.PaymentMethod;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentTransactionStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.PaymentPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.PaymentTransactionRepository;
import com.example.racehorse_transport.service.DocumentService;
import com.example.racehorse_transport.service.RefundService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final DocumentService documents;
    private final AppUserRepository users;
    private final Clock clock;
    private final QuotationRepository quotations;
    private final BookingStatusHistoryRepository history;
    private final ScheduleChangeService schedules;

    @Override
    @Transactional
    public void createPending(Long bookingId, String currency, BigDecimal amount) {
        var booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        var existing = payments.findByBooking_IdOrderById(bookingId);
        createPending(booking, existing, currency, amount);
    }

    @Override
    @Transactional
    public void refundOverpayment(Quotation quotation) {
        var booking = bookings.findForUpdate(quotation.getBooking().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        var existing = payments.findByBooking_IdOrderById(booking.getId());
        createPending(booking, existing, quotation.getCurrencyCode(), PaymentPolicy.unallocatedOverpayment(quotation, existing));
    }

    @Override
    @Transactional(readOnly = true)
    public BookingCancellationResponseDto reviewCancelledLines(Long bookingId) {
        var booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        var review = cancelledReview(booking, payments.findByBooking_IdOrderById(bookingId));
        return BookingCancellationResponseDto.from(booking, false, review.currency(), BigDecimal.ZERO,
                review.amounts().reviewable(), review.amounts().forfeited(), review.amounts().reviewable().signum() > 0
                        ? review.lines().stream().filter(BookingCancellationPolicy::canRefundLine).map(QuotationLine::getId).toList()
                        : List.of());
    }

    @Override
    @Transactional
    public List<PaymentResponseDto> refundUnspentLines(Long bookingId, List<Long> lineIds) {
        if (lineIds == null || lineIds.isEmpty()) {
            throw new BookingRuleException("Chọn ít nhất một dòng chưa chi để hoàn.");
        }
        var booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        var entries = new ArrayList<>(payments.findByBooking_IdOrderById(bookingId));
        var review = cancelledReview(booking, entries);
        BookingCancellationPolicy.includeUnspentLines(review.amounts(), review.lines(), lineIds);

        List<PaymentResponseDto> result = new ArrayList<>();
        for (var line : review.lines()) {
            if (lineIds.contains(line.getId())) {
                var created = createPending(booking, entries, review.currency(), line.getAmount());
                // Nếu dòng trải qua nhiều khoản gốc, FK đánh dấu bằng khoản hoàn đầu tiên.
                line.setRefundPayment(created.getFirst());
                entries.addAll(created);
                created.stream().map(PaymentResponseDto::from).forEach(result::add);
            }
        }
        return List.copyOf(result);
    }

    private LineReview cancelledReview(Booking booking, List<Payment> entries) {
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw BookingRuleException.conflict("Chỉ đối soát các dòng của đơn đã hủy.");
        }
        Long bookingId = booking.getId();
        var quotation = quotations.findByBooking_IdAndStatus(bookingId, QuotationStatus.ACCEPTED)
                .orElseThrow(() -> BookingRuleException.conflict("Đơn không có báo giá đã chấp nhận."));
        var cancelled = history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(bookingId, BookingStatus.CANCELLED)
                .orElseThrow(() -> BookingRuleException.conflict("Thiếu lịch sử hủy đơn."));
        boolean refundTransport = cancelled.getFromStatus() == BookingStatus.SCHEDULE_CHANGE_REQUIRED
                && schedules.cancellationTerms(bookingId).refundTransportDeposit();
        var lines = quotations.findLinesForCancellation(quotation.getId());
        var amounts = BookingCancellationPolicy.amounts(Outcome.FORFEIT_DEPOSIT, refundTransport, quotation, lines, entries);
        return new LineReview(quotation.getCurrencyCode(), lines, amounts);
    }

    private record LineReview(String currency, List<QuotationLine> lines, Breakdown amounts) {}

    private List<Payment> createPending(Booking booking, List<Payment> existing, String currency, BigDecimal amount) {
        List<Payment> created = new ArrayList<>();
        for (var allocation : PaymentPolicy.allocate(existing, currency, amount)) {
            var original = allocation.original();
            var refund = new Payment();
            refund.setBooking(booking);
            refund.setQuotation(original.getQuotation());
            refund.setRefundedPayment(original);
            refund.setPaymentType(PaymentType.REFUND);
            refund.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
            refund.setStatus(PaymentStatus.PENDING);
            refund.setCurrencyCode(currency);
            refund.setAmount(allocation.amount());
            created.add(payments.save(refund));
        }
        return created;
    }

    @Override
    @Transactional
    public PaymentResponseDto markRefunded(Long paymentId, String username, MarkRefundedRequestDto request,
                                           MultipartFile receipt) {
        Long bookingId = payments.findBookingId(paymentId).orElseThrow(RefundServiceImpl::notFound);
        bookings.findForUpdate(bookingId).orElseThrow(RefundServiceImpl::notFound);
        var refund = payments.findById(paymentId).filter(row -> row.getPaymentType() == PaymentType.REFUND)
                .orElseThrow(RefundServiceImpl::notFound);
        if (refund.getStatus() != PaymentStatus.PENDING) {
            throw BookingRuleException.conflict("Khoản hoàn không còn chờ chuyển khoản.");
        }
        String reference = request.transferReference();
        if (reference == null || reference.isBlank() || reference.strip().length() > 100) {
            throw new BookingRuleException("Mã chuyển khoản bắt buộc và tối đa 100 ký tự.");
        }
        var actor = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người thao tác."));
        var now = clock.instant();
        if (transactions.insertResult(paymentId, PaymentMethod.BANK_TRANSFER.name(), reference.strip(),
                null, "00", refund.getAmount(), PaymentTransactionStatus.SUCCESS.name(), now, "{}") == 0) {
            throw BookingRuleException.conflict("Mã chuyển khoản đã được ghi nhận.");
        }
        if (receipt != null && !receipt.isEmpty()) {
            documents.attach(receipt, "REFUND_RECEIPT", DocumentSubject.BOOKING, bookingId, actor.getId());
        }
        refund.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        refund.setStatus(PaymentStatus.PAID);
        refund.setPaidAt(now);
        return PaymentResponseDto.from(refund);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy khoản hoàn.");
    }
}
