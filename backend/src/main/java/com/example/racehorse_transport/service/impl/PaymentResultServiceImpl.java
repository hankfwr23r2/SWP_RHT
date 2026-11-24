package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.VnpayClient;
import com.example.racehorse_transport.client.VnpayClient.GatewayResult;
import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.dto.payment.VnpayIpnResponseDto;
import com.example.racehorse_transport.dto.payment.PaymentResultResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.policy.PaymentPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.PaymentTransactionRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.PaymentResultService;
import com.example.racehorse_transport.service.RefundService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentResultServiceImpl implements PaymentResultService {
    private static final Set<String> RAW_KEYS = Set.of("vnp_TxnRef", "vnp_TmnCode", "vnp_Amount", "vnp_BankCode",
            "vnp_BankTranNo", "vnp_CardType", "vnp_PayDate", "vnp_TransactionNo", "vnp_ResponseCode",
            "vnp_TransactionStatus");

    private final VnpayClient vnpay;
    private final VnpayProperties properties;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final BookingStatusService statuses;
    private final NotificationService notifications;
    private final StaffRepository staff;
    private final RefundService refunds;
    private final Clock clock;

    @Override
    @Transactional
    public VnpayIpnResponseDto receive(Map<String, String> parameters) {
        Instant receivedAt = clock.instant();
        if (!vnpay.isConfigured() || !vnpay.hasValidSignature(parameters)) {
            return new VnpayIpnResponseDto("97", "Invalid signature");
        }
        GatewayResult result = vnpay.read(parameters);
        var reference = PaymentPolicy.parseReference(result.reference());
        if (reference == null || !properties.getTmnCode().equals(parameters.get("vnp_TmnCode"))) {
            return new VnpayIpnResponseDto("01", "Order not found");
        }
        var booking = payments.findBookingId(reference.paymentId()).flatMap(bookings::findForUpdate).orElse(null);
        if (booking == null) {
            return new VnpayIpnResponseDto("01", "Order not found");
        }
        var session = transactions.findByGatewayCodeAndGatewayTransactionNumber(
                PaymentCheckoutServiceImpl.SESSION_GATEWAY, result.reference()).orElse(null);
        if (session == null || !session.getPayment().getId().equals(reference.paymentId())) {
            return new VnpayIpnResponseDto("01", "Order not found");
        }
        if (result.amount() == null || session.getAmount().compareTo(result.amount()) != 0) {
            return new VnpayIpnResponseDto("04", "Invalid amount");
        }
        String transactionNumber = transactionNumber(result);
        if (transactionNumber == null || result.responseCode() == null || !result.responseCode().matches("[0-9]{2}")) {
            return new VnpayIpnResponseDto("99", "Invalid result");
        }

        var payment = session.getPayment();
        var status = transactionStatus(result, parameters);
        String bank = result.bankCode() != null && result.bankCode().matches("[A-Za-z0-9]{1,30}") ? result.bankCode() : null;
        String gateway = transactionNumber.equals(result.transactionNumber()) ? VnpayClient.GATEWAY_CODE : "VNPAY_UNNUMBERED";
        if (transactions.insertResult(payment.getId(), gateway, transactionNumber, bank, result.responseCode(),
                result.amount(), status.name(), result.paidAt(), safePayload(parameters)) == 0) {
            return new VnpayIpnResponseDto("02", "Order already confirmed");
        }
        if (session.getStatus() == PaymentTransactionStatus.PENDING || status == PaymentTransactionStatus.SUCCESS) {
            session.setStatus(status);
            session.setResponseCode(result.responseCode());
        }
        if (result.success()) {
            applySuccess(booking, payment, result, reference, receivedAt);
        } else if ("07".equals(result.responseCode()) || "07".equals(parameters.get("vnp_TransactionStatus"))) {
            notifyManagers(booking, "VNPAY cảnh báo giao dịch cần đối soát cho đơn " + booking.getCode() + ".");
        }
        return new VnpayIpnResponseDto("00", "Confirm Success");
    }

    @Override
    @Transactional
    public PaymentResultResponseDto receiveReturn(Map<String, String> parameters) {
        var acknowledgment = receive(parameters);
        if ("00".equals(acknowledgment.code()) || "02".equals(acknowledgment.code())) {
            var gateway = vnpay.read(parameters);
            String message = switch (transactionStatus(gateway, parameters)) {
                case SUCCESS -> "VNPAY báo đã nhận tiền. Xem trạng thái khoản thu để biết kết quả ghi nhận.";
                case PENDING -> "Giao dịch đang chờ VNPAY xác nhận.";
                case FAILED -> "VNPAY báo giao dịch chưa thành công.";
            };
            return new PaymentResultResponseDto(gateway.responseCode(), message);
        }
        return new PaymentResultResponseDto(acknowledgment.code(), "Không xác minh được giao dịch.");
    }

    private void applySuccess(Booking booking, Payment payment, GatewayResult result,
                              PaymentPolicy.Session reference, Instant receivedAt) {
        if (!PaymentPolicy.canApply(payment, booking, reference, result.paidAt(), receivedAt,
                vnpay.depositConfirmationGraceMinutes(), result.amount())) {
            notifyManagers(booking, "Có tiền VNPAY cần đối soát cho đơn " + booking.getCode() + ".");
            return;
        }
        boolean balanceWasReduced = payment.getPaymentType() == PaymentType.BALANCE
                && result.amount().compareTo(payment.getAmount()) > 0;
        payment.setAmount(result.amount());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(result.paidAt());
        if (balanceWasReduced) {
            refunds.refundOverpayment(payment.getQuotation());
            notifyManagers(booking, "Khách đã trả dư qua phiên VNPAY trước khi cập nhật số dư. Kiểm tra khoản hoàn.");
        }
        if (payment.getPaymentType() == PaymentType.DEPOSIT) {
            if (booking.getWaybillNumber() == null) {
                booking.setWaybillNumber("WB-" + Long.toString(booking.getId(), 36).toUpperCase(Locale.ROOT));
            }
            statuses.transition(booking.getId(), BookingStatus.CONFIRMED, "Đã nhận đủ tiền cọc VNPAY.", null);
        }
    }

    private void notifyManagers(Booking booking, String message) {
        var recipients = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
        if (recipients.isEmpty()) {
            log.warn("Không có quản lý đang hoạt động để nhận thông báo đối soát VNPAY của đơn {}", booking.getId());
            return;
        }
        notifications.notify(recipients, booking.getId(), "Đối soát VNPAY", message, null);
    }

    private static String transactionNumber(GatewayResult result) {
        String number = result.transactionNumber();
        if (number != null && number.matches("[0-9]{1,100}") && number.matches(".*[1-9].*")) {
            return number;
        }
        return result.success() ? null : result.reference();
    }

    private static PaymentTransactionStatus transactionStatus(GatewayResult result, Map<String, String> parameters) {
        if (result.success()) {
            return PaymentTransactionStatus.SUCCESS;
        }
        String gatewayStatus = parameters.get("vnp_TransactionStatus");
        return ("00".equals(result.responseCode()) && "01".equals(gatewayStatus))
                || "07".equals(result.responseCode()) || "07".equals(gatewayStatus)
                ? PaymentTransactionStatus.PENDING : PaymentTransactionStatus.FAILED;
    }

    private String safePayload(Map<String, String> parameters) {
        var payload = JsonNodeFactory.instance.objectNode();
        RAW_KEYS.stream().sorted().filter(parameters::containsKey).forEach(key -> payload.put(key, parameters.get(key)));
        return payload.toString();
    }
}
