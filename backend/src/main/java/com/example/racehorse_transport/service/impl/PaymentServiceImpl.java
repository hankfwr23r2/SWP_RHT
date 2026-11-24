package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.VnpayClient;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.PaymentPolicy;
import com.example.racehorse_transport.policy.DepositPaymentWindowPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.PaymentService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final BookingRepository bookings;
    private final QuotationRepository quotations;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final SystemConfigService configs;
    private final NotificationService notifications;
    private final VnpayClient vnpay;
    private final BookingHorseRepository horses;
    private final DepartureService departures;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponseDto> payments(Long bookingId, String username) {
        bookings.findByIdAndCustomer_User_Username(bookingId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        return payments.findByBooking_IdOrderById(bookingId).stream().map(PaymentResponseDto::from).toList();
    }

    @Override
    @Transactional
    public void requestBalance(Long bookingId, Instant receivedAt) {
        Booking booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (booking.getStatus() != BookingStatus.CONFIRMED || receivedAt == null) {
            throw BookingRuleException.conflict("Chỉ lập khoản số dư khi nhận ngựa của đơn đã xác nhận.");
        }
        var quotation = quotations.findByBooking_IdAndStatus(bookingId, QuotationStatus.ACCEPTED)
                .orElseThrow(() -> BookingRuleException.conflict("Đơn chưa có báo giá đã chấp nhận."));
        var existing = payments.findByBooking_IdOrderById(bookingId);
        if (existing.stream().noneMatch(p -> p.getPaymentType() == PaymentType.DEPOSIT && p.getStatus() == PaymentStatus.PAID)) {
            throw BookingRuleException.conflict("Đơn chưa trả cọc.");
        }
        if (existing.stream().anyMatch(p -> p.getPaymentType() == PaymentType.BALANCE)) {
            return;
        }
        var outstanding = PaymentPolicy.outstanding(quotation, existing);
        if (outstanding.signum() <= 0) {
            return;
        }
        Instant dueAt = receivedAt.plus(configs.getInt("BALANCE_WAIT_MINUTES"), ChronoUnit.MINUTES);
        payments.save(newPayment(quotation, PaymentType.BALANCE, outstanding, dueAt));
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, "Thanh toán số dư",
                "Ngựa đã được nhận. Vui lòng thanh toán số dư để xe có thể rời nơi nhận.", null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createDeposit(Quotation quotation) {
        payments.save(newPayment(quotation, PaymentType.DEPOSIT, quotation.getDepositAmount(), quotation.getExpiresAt()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPendingDeposits(Long bookingId) {
        payments.findByBooking_IdOrderById(bookingId).stream()
                .filter(p -> p.getPaymentType() == PaymentType.DEPOSIT && p.getStatus() == PaymentStatus.PENDING)
                .forEach(p -> p.setStatus(PaymentStatus.CANCELLED));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expireDepositAndRelease(Quotation quotation, Instant now) {
        Booking booking = quotation.getBooking();
        var deposit = payments.findByBooking_IdOrderById(booking.getId()).stream()
                .filter(p -> p.getPaymentType() == PaymentType.DEPOSIT && p.getQuotation().getId().equals(quotation.getId()))
                .findFirst().orElse(null);
        if (deposit != null) {
            if (deposit.getStatus() == PaymentStatus.PAID) {
                return false;
            }
            if (now.isBefore(DepositPaymentWindowPolicy.confirmationUntil(quotation.getExpiresAt(),
                    vnpay.depositConfirmationGraceMinutes()))
                    && transactions.existsByPayment_IdAndGatewayCodeAndStatus(deposit.getId(),
                            PaymentCheckoutServiceImpl.SESSION_GATEWAY, PaymentTransactionStatus.PENDING)) {
                return false;
            }
            deposit.setStatus(PaymentStatus.CANCELLED);
        }
        if (booking.getDeparture() != null) {
            var types = horses.findForDetail(booking.getId()).stream()
                    .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE)
                    .map(horse -> horse.getStallType().getCode()).toList();
            if (!types.isEmpty()) {
                departures.release(booking.getDeparture().getId(), StallSeatPolicy.total(types));
            }
            // Không còn giữ chỗ thì không còn chuyến, để lần hủy sau không nhả chỗ lần nữa
            booking.setDeparture(null);
        }
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal reconcileBalance(Quotation quotation) {
        var existing = payments.findByBooking_IdOrderById(quotation.getBooking().getId());
        BigDecimal outstanding = PaymentPolicy.outstanding(quotation, existing);
        existing.stream().filter(p -> p.getPaymentType() == PaymentType.BALANCE && p.getStatus() == PaymentStatus.PENDING)
                .forEach(p -> {
                    if (outstanding.signum() > 0) {
                        p.setAmount(outstanding);
                        p.setQuotation(quotation);
                    } else {
                        p.setStatus(PaymentStatus.CANCELLED);
                    }
                });
        return outstanding;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingBalanceConfirmation(Long bookingId) {
        Instant now = clock.instant();
        return transactions.findByPayment_Booking_IdAndPayment_PaymentTypeAndGatewayCodeAndStatus(bookingId,
                        PaymentType.BALANCE, PaymentCheckoutServiceImpl.SESSION_GATEWAY, PaymentTransactionStatus.PENDING)
                .stream().anyMatch(session -> PaymentPolicy.awaitsGateway(
                        PaymentPolicy.parseReference(session.getGatewayTransactionNumber()), session.getPayment().getDueAt(),
                        now, vnpay.defaultExpireMinutes(), vnpay.depositConfirmationGraceMinutes()));
    }

    @Override
    @Transactional
    public Long createExtraCharge(Long bookingId, List<Long> costItemIds, Long actorUserId) {
        throw new UnsupportedOperationException("Khoản thu thêm thuộc A8.");
    }

    private static Payment newPayment(Quotation quotation, PaymentType type, BigDecimal amount, Instant dueAt) {
        var payment = new Payment();
        payment.setBooking(quotation.getBooking());
        payment.setQuotation(quotation);
        payment.setPaymentType(type);
        payment.setAmount(amount);
        payment.setCurrencyCode(quotation.getCurrencyCode());
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setDueAt(dueAt);
        return payment;
    }
}
