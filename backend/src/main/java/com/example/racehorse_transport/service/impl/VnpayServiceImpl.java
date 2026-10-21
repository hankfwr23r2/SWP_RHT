package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.config.VnpayProperties;
import com.example.racehorse_transport.dto.payment.VnpayCreateRequest;
import com.example.racehorse_transport.dto.payment.VnpayCreateResponse;
import com.example.racehorse_transport.dto.payment.VnpayResultResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.SettlementService;
import com.example.racehorse_transport.service.VnpayService;
import com.example.racehorse_transport.util.VnpayUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // Đơn đã đóng thì không nhận thanh toán nữa
    private static final Set<String> CLOSED_STATUSES = Set.of("cancelled", "completed", "quote_expired");

    private final VnpayProperties props;
    private final BookingRepository bookingRepository;
    private final QuotationRepository quotationRepository;
    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final SettlementService settlementService;

    @Override
    @Transactional
    public VnpayCreateResponse createPaymentUrl(VnpayCreateRequest request, String username, String clientIp) {
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

        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.getExpireMinutes(), ChronoUnit.MINUTES);
        BigDecimal amount;
        String orderInfo;
        String prefix;
        switch (type) {
            case "DEPOSIT" -> {
                if (!"awaiting_payment".equalsIgnoreCase(booking.getStatus())) {
                    throw new IllegalStateException("Đơn hàng không ở bước chờ thanh toán đặt cọc!");
                }
                if (hasCompleted(payments, "DEPOSIT")) {
                    throw new IllegalStateException("Đơn hàng này đã đặt cọc 30%!");
                }
                Quotation quotation = latestQuotation(booking);
                if (quotation.getExpiresAt() != null) {
                    if (now.isAfter(quotation.getExpiresAt())) {
                        throw new IllegalStateException("Báo giá đã hết hạn " + BookingServiceImpl.QUOTE_VALID_HOURS
                                + " giờ, không thể đặt cọc!");
                    }
                    // Link thanh toán không sống lâu hơn báo giá
                    if (quotation.getExpiresAt().isBefore(expiresAt)) {
                        expiresAt = quotation.getExpiresAt();
                    }
                }
                amount = quotation.getDepositAmount();
                orderInfo = "Thanh toan coc 30% don BK-" + booking.getId();
                prefix = "DEP";
            }
            case "BALANCE" -> {
                ensureOpen(booking);
                if (!hasCompleted(payments, "DEPOSIT") && booking.getWaybillNo() == null) {
                    throw new IllegalStateException("Đơn hàng chưa thanh toán tiền cọc 30%! Vui lòng cọc trước khi thanh toán số dư.");
                }
                if (hasCompleted(payments, "BALANCE")) {
                    throw new IllegalStateException("Đơn hàng này đã thanh toán đủ 70% còn lại!");
                }
                Quotation quotation = latestQuotation(booking);
                amount = quotation.getBalanceAmount() != null
                        ? quotation.getBalanceAmount()
                        : quotation.getTotalAmount().multiply(new BigDecimal("0.7"));
                orderInfo = "Thanh toan 70% con lai don BK-" + booking.getId();
                prefix = "BAL";
            }
            case "SETTLEMENT" -> {
                if (hasCompleted(payments, "SETTLEMENT")) {
                    throw new IllegalStateException("Đơn hàng này đã thanh toán quyết toán!");
                }
                amount = settlementService.holdForVnpay(booking, request.getRating());
                orderInfo = "Thanh toan quyet toan phat sinh don BK-" + booking.getId();
                prefix = "SET";
            }
            default -> throw new IllegalArgumentException("Loại thanh toán không hợp lệ: " + request.getPaymentType());
        }

        amount = amount == null ? BigDecimal.ZERO : amount.setScale(0, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new IllegalStateException("Số tiền cần thanh toán không hợp lệ!");
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
        params.put("vnp_ReturnUrl", props.getReturnUrl());
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
        if (!VnpayUtil.verify(params, props.getHashSecret())) {
            log.warn("VNPAY callback with invalid signature, txnRef={}", params.get("vnp_TxnRef"));
            return result(false, "97", "Chữ ký không hợp lệ, giao dịch không được ghi nhận.", null);
        }

        String txnRef = params.get("vnp_TxnRef");
        Optional<Payment> found = txnRef == null ? Optional.empty() : paymentRepository.findByTransactionCode(txnRef);
        if (found.isEmpty() || !"VNPAY".equalsIgnoreCase(found.get().getPaymentMethod())) {
            return result(false, "01", "Không tìm thấy giao dịch " + txnRef + ".", null);
        }
        Payment payment = found.get();

        String expectedAmount = payment.getAmount().setScale(0, RoundingMode.HALF_UP).multiply(HUNDRED).toBigInteger().toString();
        if (!expectedAmount.equals(params.get("vnp_Amount"))) {
            log.warn("VNPAY amount mismatch txnRef={}, expected={}, received={}", txnRef, expectedAmount, params.get("vnp_Amount"));
            return result(false, "04", "Số tiền giao dịch không khớp.", payment);
        }

        // IPN và Return URL đều gọi vào đây: bản ghi đã xử lý thì chỉ trả lại kết quả cũ
        if (!"PENDING".equalsIgnoreCase(payment.getStatus())) {
            return result("COMPLETED".equalsIgnoreCase(payment.getStatus()), "02", describeStatus(payment), payment);
        }

        payment.setGatewayTransactionNo(params.get("vnp_TransactionNo"));
        payment.setBankCode(params.get("vnp_BankCode"));
        String responseCode = params.get("vnp_ResponseCode");
        boolean paid = "00".equals(responseCode) && "00".equals(params.get("vnp_TransactionStatus"));

        if (!paid) {
            payment.setStatus("FAILED");
            payment.setNote("VNPAY từ chối (mã " + responseCode + "): " + describeResponseCode(responseCode));
            paymentRepository.save(payment);
            return result(false, "00", describeResponseCode(responseCode), payment);
        }

        payment.setPaymentDate(Instant.now());
        Booking booking = payment.getBookingID();
        List<Payment> others = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        if (!canApply(payment, booking, others)) {
            // Đã trừ tiền nhưng đơn không còn ở bước này (hết hạn báo giá, đã hủy, đã trả trước đó) -> Quản lý hoàn tiền
            payment.setStatus("NEEDS_REVIEW");
            payment.setNote("Đã nhận tiền VNPAY nhưng đơn không còn ở bước thanh toán (trạng thái " + booking.getStatus()
                    + "). Cần Quản lý kiểm tra và hoàn tiền.");
            paymentRepository.save(payment);
            log.warn("VNPAY payment txnRef={} needs review, booking ID={} status={}", txnRef, booking.getId(), booking.getStatus());
            return result(false, "00", "Đã nhận tiền nhưng đơn không còn ở bước thanh toán. Nhà xe sẽ liên hệ để hoàn tiền.", payment);
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
            case "SETTLEMENT" -> ("settlement_issued".equalsIgnoreCase(booking.getStatus())
                    || "payment_overdue".equalsIgnoreCase(booking.getStatus())) && !hasCompleted(payments, "SETTLEMENT");
            default -> false;
        };
    }

    private Booking findOwnedBooking(String bookingIdentifier, String username) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));
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
            case "NEEDS_REVIEW" -> "Đã nhận tiền nhưng đơn không còn ở bước thanh toán. Nhà xe sẽ liên hệ để hoàn tiền.";
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
}
