package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.VnpayClient;
import com.example.racehorse_transport.dto.payment.PaymentCheckoutResponseDto;
import com.example.racehorse_transport.entity.PaymentTransaction;
import com.example.racehorse_transport.entity.enums.PaymentTransactionStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.PaymentPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.PaymentTransactionRepository;
import com.example.racehorse_transport.service.PaymentCheckoutService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCheckoutServiceImpl implements PaymentCheckoutService {
    static final String SESSION_GATEWAY = "VNPAY_SESSION";

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final VnpayClient vnpay;
    private final Clock clock;

    @Override
    @Transactional
    public PaymentCheckoutResponseDto checkout(Long paymentId, String username, String ipAddress) {
        Long bookingId = payments.findBookingId(paymentId).orElseThrow(PaymentCheckoutServiceImpl::notFound);
        var booking = bookings.findForUpdate(bookingId)
                .filter(row -> username.equals(row.getCustomer().getUser().getUsername()))
                .orElseThrow(PaymentCheckoutServiceImpl::notFound);
        var payment = payments.findById(paymentId).orElseThrow(PaymentCheckoutServiceImpl::notFound);
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        if (!PaymentPolicy.isOpen(payment, booking, now) || !"VND".equals(payment.getCurrencyCode())
                || !VnpayClient.isPayable(payment.getAmount()) || !vnpay.isConfigured()) {
            throw BookingRuleException.conflict("Khoản thu hiện không thể thanh toán qua VNPAY.");
        }
        var expiry = now.plus(vnpay.defaultExpireMinutes(), ChronoUnit.MINUTES);
        if (payment.getPaymentType() == PaymentType.DEPOSIT && payment.getDueAt().isBefore(expiry)) {
            expiry = payment.getDueAt();
        }
        String reference = PaymentPolicy.reference(paymentId, now, UUID.randomUUID());
        String url = vnpay.createPaymentUrl(reference, payment.getAmount(), "Thanh toan " + booking.getCode(),
                ipAddress, now, expiry);
        var session = new PaymentTransaction();
        session.setPayment(payment);
        session.setGatewayCode(SESSION_GATEWAY);
        session.setGatewayTransactionNumber(reference);
        session.setResponseCode("PENDING");
        session.setAmount(payment.getAmount());
        session.setStatus(PaymentTransactionStatus.PENDING);
        session.setRawPayload("{}");
        transactions.save(session);
        return new PaymentCheckoutResponseDto(url, expiry);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy khoản thanh toán.");
    }
}
