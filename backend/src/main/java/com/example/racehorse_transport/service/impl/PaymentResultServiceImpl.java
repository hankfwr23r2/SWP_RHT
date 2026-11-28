package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.VnpayClient;
import com.example.racehorse_transport.client.VnpayClient.GatewayResult;
import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.dto.payment.VnpayIpnResponseDto;
import com.example.racehorse_transport.dto.payment.PaymentResultResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.policy.DepositPaymentWindowPolicy;
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
import java.util.List;
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
        var gateway = vnpay.read(parameters);
        var reference = PaymentPolicy.parseReference(gateway.reference());
        var payment = reference == null ? null : payments.findById(reference.paymentId()).orElse(null);
        if (payment == null || !"00".equals(acknowledgment.code()) && !"02".equals(acknowledgment.code())) {
            return PaymentResultResponseDto.unverified(acknowledgment.code(), "Không xác minh được giao dịch.");
        }
        boolean paid = gateway.success();
        // So với giờ trả đã ghi ở lần nhận đầu: tua giờ thêm rồi tính lại từ vnp_PayDate sẽ ra giờ khác
        boolean success = paid && transactions.findByGatewayCodeAndGatewayTransactionNumber(
                        VnpayClient.GATEWAY_CODE, gateway.transactionNumber())
                .filter(row -> PaymentPolicy.isAppliedBy(payment, gateway.amount(), row.getGatewayPaidAt()))
                .isPresent();
        return PaymentResultResponseDto.from(payment, success, paid && !success, gateway.responseCode(),
                success ? "Thanh toán thành công." : paid
                        ? "Đã nhận tiền nhưng chưa thể xác nhận đơn. Nhà xe sẽ kiểm tra và liên hệ hoàn tiền."
                        : describe(transactionStatus(gateway, parameters), gateway.responseCode()),
                gateway.amount(), gateway.transactionNumber());
    }

    // Bảng mã vnp_ResponseCode theo tài liệu VNPAY
    private static String describe(PaymentTransactionStatus status, String code) {
        return switch (code) {
            case "07" -> "Giao dịch bị nghi ngờ gian lận, đang chờ ngân hàng xác minh.";
            case "09" -> "Thẻ hoặc tài khoản chưa đăng ký dịch vụ Internet Banking.";
            case "10" -> "Xác thực thông tin thẻ hoặc tài khoản sai quá 3 lần.";
            case "11" -> "Đã hết thời gian chờ thanh toán.";
            case "12" -> "Thẻ hoặc tài khoản bị khóa.";
            case "13" -> "Nhập sai mật khẩu xác thực giao dịch (OTP).";
            case "24" -> "Bạn đã hủy giao dịch.";
            case "51" -> "Tài khoản không đủ số dư.";
            case "65" -> "Tài khoản đã vượt quá hạn mức giao dịch trong ngày.";
            case "75" -> "Ngân hàng thanh toán đang bảo trì.";
            case "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định.";
            default -> status == PaymentTransactionStatus.PENDING ? "Giao dịch đang chờ VNPAY xác nhận."
                    : "Giao dịch không thành công.";
        };
    }

    private void applySuccess(Booking booking, Payment payment, GatewayResult result,
                              PaymentPolicy.Session reference, Instant receivedAt) {
        if (!PaymentPolicy.canApply(payment, booking, reference, result.paidAt(), receivedAt,
                vnpay.depositConfirmationGraceMinutes(), result.amount())) {
            boolean outsideWindow = payment.getPaymentType() == PaymentType.DEPOSIT && payment.getDueAt() != null
                    && !DepositPaymentWindowPolicy.canConfirm(payment.getDueAt(), reference.createdAt(), result.paidAt(),
                            receivedAt, vnpay.depositConfirmationGraceMinutes());
            notifyManagers(booking, "Đã nhận " + result.amount().toBigInteger() + " VND (mã VNPAY " + result.transactionNumber()
                    + ") cho đơn " + booking.getCode() + " nhưng không ghi nhận vào đơn: "
                    + (outsideWindow || result.paidAt() == null
                            ? "thanh toán ngoài hạn hoặc thiếu thời điểm thanh toán hợp lệ từ VNPAY."
                            : "đơn hoặc khoản thu không còn nhận thanh toán (" + booking.getStatus() + ").")
                    + " Cần kiểm tra và hoàn tiền cho khách.");
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
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(),
                    "Đặt cọc thành công", "Đã nhận tiền cọc của đơn " + booking.getCode() + ".",
                    "/orders/" + booking.getId());
        }
    }

    private void notifyManagers(Booking booking, String message) {
        var recipients = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
        if (recipients.isEmpty()) {
            log.warn("Không có quản lý đang hoạt động để nhận thông báo đối soát VNPAY của đơn {}", booking.getId());
            return;
        }
        notifications.notify(recipients, booking.getId(), "Đối soát VNPAY", message, "/manager/orders/" + booking.getId());
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
