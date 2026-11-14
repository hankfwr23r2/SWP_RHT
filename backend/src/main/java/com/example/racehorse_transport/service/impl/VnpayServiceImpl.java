package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.dto.payment.VnpayCreateRequest;
import com.example.racehorse_transport.dto.payment.VnpayCreateResponse;
import com.example.racehorse_transport.dto.payment.VnpayResultResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.SettlementService;
import com.example.racehorse_transport.service.VnpayService;
import com.example.racehorse_transport.util.CustomerBookingRules;
import com.example.racehorse_transport.util.DepositPaymentWindow;
import com.example.racehorse_transport.util.PaymentAmountLimits;
import com.example.racehorse_transport.util.VnpayUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class VnpayServiceImpl implements VnpayService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VN_ZONE);
    private static final DateTimeFormatter VNP_PAY_DATE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // Đơn đã đóng thì không nhận thanh toán nữa
    private static final Set<String> CLOSED_STATUSES = Set.of("cancelled", "completed", "quote_expired");

    private final VnpayProperties props;
    private final BookingRepository bookingRepository;
    private final QuotationRepository quotationRepository;
    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final SettlementService settlementService;
    private final Clock clock;

    @Override
    @Transactional
    public VnpayCreateResponse createPaymentUrl(VnpayCreateRequest request, String username, String clientIp) {
        if ("SETTLEMENT".equalsIgnoreCase(request.getPaymentType())) {
            throw new IllegalArgumentException("Chi phí sự cố do công ty chịu; khách hàng không thanh toán quyết toán.");
        }
        if (!props.isConfigured()) {
            List<String> missingConfig = new ArrayList<>();
            if (props.getTmnCode() == null || props.getTmnCode().isBlank()) {
                missingConfig.add("VNPAY_TMN_CODE");
            }
            if (props.getHashSecret() == null || props.getHashSecret().isBlank()) {
                missingConfig.add("VNPAY_HASH_SECRET");
            }
            throw new IllegalStateException("Hệ thống chưa cấu hình VNPAY. Thiếu biến môi trường: "
                    + String.join(", ", missingConfig) + ".");
        }
        String type = request.getPaymentType().trim().toUpperCase();
        Booking booking = findOwnedBooking(request.getBookingId(), username);
        List<Payment> payments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());

        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.getExpireMinutes(), ChronoUnit.MINUTES);
        BigDecimal amount;
        String orderInfo;
        String prefix;
        switch (type) {
            case "DEPOSIT" -> {
                com.example.racehorse_transport.util.BookingSchedulePolicy.requireCurrentSchedule(booking, now);
                if (!"awaiting_payment".equalsIgnoreCase(booking.getStatus())) {
                    throw new IllegalStateException("Đơn hàng không ở bước chờ thanh toán đặt cọc!");
                }
                if (hasCompleted(payments, "DEPOSIT")) {
                    throw new IllegalStateException("Đơn hàng này đã thanh toán tiền đặt cọc!");
                }
                Quotation quotation = latestQuotation(booking);
                if (quotation.getExpiresAt() == null) {
                    throw new IllegalStateException("Báo giá thiếu thời hạn thanh toán, vui lòng liên hệ nhà xe.");
                }
                Instant quoteDeadline = DepositPaymentWindow.expiresAt(quotation);
                Instant scheduleDeadline = com.example.racehorse_transport.util.BookingSchedulePolicy.deadline(booking);
                if (scheduleDeadline != null && scheduleDeadline.isBefore(quoteDeadline)) quoteDeadline = scheduleDeadline;
                if (!now.isBefore(quoteDeadline)) {
                    throw new IllegalStateException("Báo giá đã hết hạn, không thể đặt cọc!");
                }
                // Link thanh toán không sống lâu hơn báo giá
                if (quoteDeadline.isBefore(expiresAt)) {
                    expiresAt = quoteDeadline;
                }
                amount = quotation.getDepositAmount();
                orderInfo = "Thanh toan coc don BK-" + booking.getId();
                prefix = "DEP";
            }
            case "BALANCE" -> {
                ensureOpen(booking);
                if (!hasCompleted(payments, "DEPOSIT") && booking.getWaybillNo() == null) {
                    throw new IllegalStateException("Đơn hàng chưa thanh toán tiền đặt cọc! Vui lòng cọc trước khi thanh toán số dư.");
                }
                if (hasCompleted(payments, "BALANCE")) {
                    throw new IllegalStateException("Đơn hàng này đã thanh toán đủ 70% còn lại!");
                }
                if (!CustomerBookingRules.canPayBalance(booking.getStatus(), false) || !bookingService.isPickupComplete(booking)) {
                    throw new IllegalStateException("Chỉ thanh toán 70% còn lại khi kíp xe đã nhận ngựa và ký biên bản giao nhận ở điểm đón.");
                }
                Quotation quotation = latestQuotation(booking);
                amount = quotation.getBalanceAmount() != null
                        ? quotation.getBalanceAmount()
                        : quotation.getTotalAmount().multiply(new BigDecimal("0.7"));
                orderInfo = "Thanh toan con lai don BK-" + booking.getId();
                prefix = "BAL";
            }
            default -> throw new IllegalArgumentException("Loại thanh toán không hợp lệ: " + request.getPaymentType());
        }

        amount = amount == null ? BigDecimal.ZERO : amount.setScale(0, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new IllegalStateException("Số tiền cần thanh toán không hợp lệ!");
        }
        if (PaymentAmountLimits.exceedsMaximum(amount)) {
            throw new IllegalStateException("Số tiền cần thanh toán vượt quá giới hạn được hỗ trợ.");
        }

        // Dọn dẹp các giao dịch PENDING trước đó của cùng loại để không bị kẹt transaction dở dang
        for (Payment p : payments) {
            if (type.equalsIgnoreCase(p.getPaymentType()) && "PENDING".equalsIgnoreCase(p.getStatus())) {
                p.setStatus("CANCELLED");
                p.setNote("Hủy do người dùng tạo phiên thanh toán VNPAY mới");
                paymentRepository.save(p);
            }
        }

        String txnRef = prefix + String.format("%04d", booking.getId()) + now.toEpochMilli();
        Payment payment = Payment.builder()
                .bookingID(booking)
                .amount(amount)
                .paymentDate(now)
                .paymentType(type)
                .paymentMethod("VNPAY")
                .transactionCode(txnRef)
                .status("PENDING")
                .note("Chờ thanh toán qua VNPAY: " + orderInfo)
                .build();
        paymentRepository.save(payment);

        String returnUrl = (request.getReturnUrl() != null && request.getReturnUrl().trim().startsWith("http"))
                ? request.getReturnUrl().trim()
                : props.getReturnUrl();

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", VnpayProperties.VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", props.getTmnCode());
        params.put("vnp_Amount", amount.multiply(HUNDRED).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", VNP_DATE.format(now));
        params.put("vnp_ExpireDate", VNP_DATE.format(expiresAt));

        String paymentUrl = VnpayUtil.buildPaymentUrl(props.getPayUrl(), params, props.getHashSecret());
        log.info("Created VNPAY {} payment txnRef={} for booking ID={}, amount={}", type, txnRef, booking.getId(), amount);

        return VnpayCreateResponse.builder()
                .paymentUrl(paymentUrl)
                .transactionCode(txnRef)
                .amount(amount)
                .expiresAt(expiresAt.toEpochMilli())
                .build();
    }

    @Override
    @Transactional
    public VnpayResultResponse confirm(Map<String, String> params) {
        Instant receivedAt = clock.instant();
        if (!VnpayUtil.verify(params, props.getHashSecret())) {
            log.warn("VNPAY callback with invalid signature, txnRef={}", params.get("vnp_TxnRef"));
            return result(false, "97", "Chữ ký không hợp lệ, giao dịch không được ghi nhận.", null);
        }

        String txnRef = params.get("vnp_TxnRef");
        Optional<Integer> bookingId = txnRef == null ? Optional.empty() : paymentRepository.findBookingIdByTransactionCode(txnRef);
        if (bookingId.isEmpty()) {
            return result(false, "01", "Không tìm thấy giao dịch " + txnRef + ".", null);
        }
        // Cùng khóa với tác vụ hết hạn. Không đọc entity booking/payment trước khi lấy khóa booking.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId.get()).orElse(null);
        if (booking == null) {
            return result(false, "01", "Không tìm thấy đơn hàng của giao dịch " + txnRef + ".", null);
        }
        Optional<Payment> found = paymentRepository.findByTransactionCode(txnRef);
        if (found.isEmpty() || !"VNPAY".equalsIgnoreCase(found.get().getPaymentMethod())) {
            return result(false, "01", "Không tìm thấy giao dịch " + txnRef + ".", null);
        }
        Payment payment = found.get();

        String expectedAmount = payment.getAmount().setScale(0, RoundingMode.HALF_UP).multiply(HUNDRED).toBigInteger().toString();
        if (!expectedAmount.equals(params.get("vnp_Amount"))) {
            log.warn("VNPAY amount mismatch txnRef={}, expected={}, received={}", txnRef, expectedAmount, params.get("vnp_Amount"));
            return result(false, "04", "Số tiền giao dịch không khớp.", payment);
        }

        String responseCode = params.get("vnp_ResponseCode");
        boolean paid = "00".equals(responseCode) && "00".equals(params.get("vnp_TransactionStatus"));
        boolean replaced = "CANCELLED".equalsIgnoreCase(payment.getStatus());
        // Phiên cũ bị thay thế vẫn có thể có tiền về: phải lưu đối soát thay vì bỏ qua tiền thực nhận.
        if (!"PENDING".equalsIgnoreCase(payment.getStatus()) && !(replaced && paid)) {
            return result("COMPLETED".equalsIgnoreCase(payment.getStatus()), "02", describeStatus(payment), payment);
        }

        payment.setGatewayTransactionNo(params.get("vnp_TransactionNo"));
        payment.setBankCode(params.get("vnp_BankCode"));

        if (!paid) {
            payment.setStatus("FAILED");
            payment.setNote("VNPAY từ chối (mã " + responseCode + "): " + describeResponseCode(responseCode));
            paymentRepository.save(payment);
            return result(false, "00", describeResponseCode(responseCode), payment);
        }

        Instant paidAt = gatewayPaidAt(params.get("vnp_PayDate"));
        List<Payment> others = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        boolean withinDepositWindow = true;
        if ("DEPOSIT".equalsIgnoreCase(payment.getPaymentType())) {
            Optional<Quotation> quote = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId());
            withinDepositWindow = quote.isPresent() && quote.get().getExpiresAt() != null
                    && "PENDING".equalsIgnoreCase(quote.get().getStatus())
                    && DepositPaymentWindow.canConfirm(quote.get(), payment, paidAt, receivedAt,
                            props.getDepositConfirmationGraceMinutes())
                    && !com.example.racehorse_transport.util.BookingSchedulePolicy.overdue(booking, paidAt);
        }
        payment.setPaymentDate(paidAt != null ? paidAt : receivedAt);
        if (replaced || !canApply(payment, booking, others) || !withinDepositWindow) {
            return needsReview(payment, !withinDepositWindow
                    ? "Thanh toán ngoài hạn hoặc thiếu thời điểm thanh toán hợp lệ từ VNPAY."
                    : "Đơn/phiên thanh toán không còn hợp lệ (" + booking.getStatus() + ").");
        }

        payment.setStatus("COMPLETED");
        payment.setNote("Thanh toán " + payment.getPaymentType() + " qua VNPAY thành công, mã GD VNPAY "
                + payment.getGatewayTransactionNo());
        paymentRepository.save(payment);

        String actor = customerName(booking);
        switch (payment.getPaymentType().toUpperCase()) {
            case "DEPOSIT" -> bookingService.confirmDepositPaid(payment, actor);
            case "BALANCE" -> bookingService.confirmBalancePaid(payment, actor);
            case "SETTLEMENT" -> settlementService.confirmSettlementPaid(payment, actor);
            default -> throw new IllegalStateException("Loại thanh toán không hỗ trợ: " + payment.getPaymentType());
        }
        log.info("VNPAY payment txnRef={} confirmed for booking ID={}", txnRef, booking.getId());
        return result(true, "00", "Thanh toán thành công.", payment);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private boolean canApply(Payment payment, Booking booking, List<Payment> payments) {
        return switch (payment.getPaymentType().toUpperCase()) {
            case "DEPOSIT" -> "awaiting_payment".equalsIgnoreCase(booking.getStatus()) && !hasCompleted(payments, "DEPOSIT");
            case "BALANCE" -> !isClosed(booking) && !hasCompleted(payments, "BALANCE");
            case "SETTLEMENT" -> false; // retired customer charge; incoming money requires reconciliation
            default -> false;
        };
    }

    private Booking findOwnedBooking(String bookingIdentifier, String username) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
        User owner = booking.getCustomerID() != null ? booking.getCustomerID().getUser() : null;
        boolean ownsBooking = owner != null && username != null
                && (username.equals(owner.getUsername()) || username.equals(owner.getEmail()));
        if (!ownsBooking) {
            throw new AccessDeniedException("Bạn không có quyền thanh toán cho đơn này.");
        }
        return booking;
    }

    private Quotation latestQuotation(Booking booking) {
        return quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy thông tin báo giá của đơn hàng!"));
    }

    private void ensureOpen(Booking booking) {
        if (isClosed(booking)) {
            throw new IllegalStateException("Không thể thanh toán cho đơn hàng ở trạng thái: " + booking.getStatus());
        }
    }

    private boolean isClosed(Booking booking) {
        return booking.getStatus() != null && CLOSED_STATUSES.contains(booking.getStatus().toLowerCase());
    }

    private boolean hasCompleted(List<Payment> payments, String type) {
        return payments.stream().anyMatch(p -> type.equalsIgnoreCase(p.getPaymentType())
                && "COMPLETED".equalsIgnoreCase(p.getStatus()));
    }

    private String customerName(Booking booking) {
        User owner = booking.getCustomerID() != null ? booking.getCustomerID().getUser() : null;
        return owner != null && owner.getUsername() != null ? owner.getUsername() : "Khách hàng";
    }

    private Integer parseBookingId(String bookingIdentifier) {
        if (bookingIdentifier == null || bookingIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }
        String cleanId = bookingIdentifier.trim();
        try {
            if (cleanId.toUpperCase().startsWith("BK-")) {
                return Integer.parseInt(cleanId.substring(3).trim());
            } else if (cleanId.toUpperCase().startsWith("ORD-")) {
                String[] parts = cleanId.split("-");
                return Integer.parseInt(parts[parts.length - 1].trim());
            }
            return Integer.parseInt(cleanId);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Định dạng mã đơn không hợp lệ: " + bookingIdentifier);
        }
    }

    private String describeStatus(Payment payment) {
        return switch (payment.getStatus().toUpperCase()) {
            case "COMPLETED" -> "Thanh toán thành công.";
            case "NEEDS_REVIEW" -> "Đã nhận tiền nhưng chưa thể xác nhận đơn. Nhà xe sẽ kiểm tra và liên hệ hoàn tiền.";
            default -> "Giao dịch không thành công.";
        };
    }

    // Bảng mã vnp_ResponseCode theo tài liệu VNPAY
    private String describeResponseCode(String code) {
        if (code == null) {
            return "Giao dịch không thành công.";
        }
        return switch (code) {
            case "07" -> "Giao dịch bị nghi ngờ gian lận, đang chờ ngân hàng xác minh.";
            case "09" -> "Thẻ/Tài khoản chưa đăng ký dịch vụ Internet Banking.";
            case "10" -> "Xác thực thông tin thẻ/tài khoản sai quá 3 lần.";
            case "11" -> "Đã hết thời gian chờ thanh toán.";
            case "12" -> "Thẻ/Tài khoản bị khóa.";
            case "13" -> "Nhập sai mật khẩu xác thực giao dịch (OTP).";
            case "24" -> "Bạn đã hủy giao dịch.";
            case "51" -> "Tài khoản không đủ số dư.";
            case "65" -> "Tài khoản đã vượt quá hạn mức giao dịch trong ngày.";
            case "75" -> "Ngân hàng thanh toán đang bảo trì.";
            case "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định.";
            default -> "Giao dịch không thành công (mã " + code + ").";
        };
    }

    private VnpayResultResponse result(boolean success, String rspCode, String message, Payment payment) {
        VnpayResultResponse.VnpayResultResponseBuilder builder = VnpayResultResponse.builder()
                .success(success)
                .rspCode(rspCode)
                .message(message);
        if (payment != null) {
            Booking booking = payment.getBookingID();
            builder.bookingId(booking != null ? booking.getId() : null)
                    .bookingCode(booking != null ? String.format("BK-%03d", booking.getId()) : null)
                    .paymentType(payment.getPaymentType())
                    .paymentStatus(payment.getStatus())
                    .amount(payment.getAmount())
                    .transactionCode(payment.getTransactionCode())
                    .gatewayTransactionNo(payment.getGatewayTransactionNo())
                    .bankCode(payment.getBankCode());
        }
        return builder.build();
    }

    private Instant gatewayPaidAt(String value) {
        if (value == null || !value.matches("[0-9]{14}")) return null;
        try {
            return LocalDateTime.parse(value, VNP_PAY_DATE).atZone(VN_ZONE).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private VnpayResultResponse needsReview(Payment payment, String reason) {
        payment.setStatus("NEEDS_REVIEW");
        payment.setNote("Đã nhận tiền VNPAY. " + reason + " Cần Quản lý kiểm tra và hoàn tiền.");
        paymentRepository.save(payment);
        log.warn("VNPAY payment txnRef={} needs review: {}", payment.getTransactionCode(), reason);
        return result(false, "00", "Đã nhận tiền nhưng chưa thể xác nhận đơn. Nhà xe sẽ kiểm tra và liên hệ hoàn tiền.", payment);
    }
}
