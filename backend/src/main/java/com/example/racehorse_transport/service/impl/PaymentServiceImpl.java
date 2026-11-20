package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.payment.CancelBookingRequestDto;
import com.example.racehorse_transport.dto.payment.CancelWithRefundResponseDto;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.dto.payment.PaymentSummaryResponseDto;
import com.example.racehorse_transport.dto.payment.ReconcileInsuranceRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.PaymentService;
import com.example.racehorse_transport.service.StaffNotificationService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.BookingHistory;
import com.example.racehorse_transport.util.CustomerBookingRules;
import com.example.racehorse_transport.util.HorseInsurancePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final QuotationRepository quotationRepository;
    private final ObjectMapper objectMapper;
    private final StaffNotificationService staffNotifier;
    private final BookingService bookingService;
    // Tiêm bằng field để không đổi chữ ký constructor; null (test dựng tay) thì câu báo không kèm số
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.racehorse_transport.service.SystemConfigService systemConfigService;

    // Quản lý hủy chuyến được từ lúc khách đặt cọc tới trước khi xe rời điểm đón
    private static final Set<String> TRIP_CANCELLABLE_STATUSES = Set.of(
            "waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup",
            "en_route_to_pickup", "pickup_failed");

    @Override
    @Transactional
    public void reconcileInsurance(String identifier, ReconcileInsuranceRequestDto request, String actor) {
        Booking booking = bookingRepository.findByIdForUpdate(parseBookingId(identifier)).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (!Set.of("cancelled", "returning_horses").contains(booking.getStatus())) throw new IllegalStateException("Chỉ đối soát đơn đã hủy.");
        BookingDetailsPayloadDto payload = extractPayload(booking);
        Map<String, Object> payment = getOrCreatePaymentMap(payload);
        if (!Boolean.TRUE.equals(payment.get("insuranceReconciliationPending"))) throw new IllegalStateException("Đơn không có khoản bảo hiểm chờ đối soát hoặc đã đối soát.");
        BigDecimal collected = new BigDecimal(payment.get("insuranceCollected").toString());
        BigDecimal premium = BigDecimal.ZERO;
        if (payload.getQuote() instanceof Map<?, ?> quote && quote.get("lines") instanceof List<?> lines) {
            for (Object row : lines) if (row instanceof Map<?, ?> line && HorseInsurancePolicy.LABEL.equals(line.get("label"))) premium = premium.add(new BigDecimal(line.get("amount").toString()));
        }
        if (request.getTransferredAmount().compareTo(premium) > 0 || request.getProviderRefundAmount().compareTo(request.getTransferredAmount()) > 0)
            throw new IllegalArgumentException("Số chuyển đối tác hoặc số đối tác hoàn không hợp lệ.");
        BigDecimal recoverable = collected.subtract(request.getTransferredAmount()).add(request.getProviderRefundAmount()).max(BigDecimal.ZERO).min(collected);
        if (request.getCustomerRefundAmount().compareTo(recoverable) != 0)
            throw new IllegalArgumentException("Tiền hoàn khách phải bằng số thu hộ trừ số chuyển đối tác cộng số đối tác hoàn (giới hạn trong số đã thu).");
        // Ghi nhận chuyển tiền hoàn thực hiện bên ngoài, không tự gọi cổng thanh toán.
        if (request.getCustomerRefundAmount().signum() > 0) paymentRepository.save(Payment.builder().bookingID(booking)
            .amount(request.getCustomerRefundAmount()).paymentDate(Instant.now()).paymentType("REFUND").paymentMethod("MANUAL")
            .transactionCode(request.getReference()).status("COMPLETED").note("Hoàn bảo hiểm thu hộ: " + request.getNote()).build());
        payment.put("refundAmount", request.getCustomerRefundAmount());
        payment.put("insuranceTransferred", request.getTransferredAmount());
        payment.put("insuranceProviderRefund", request.getProviderRefundAmount());
        payment.put("insuranceReconciliationPending", false);
        payment.put("insuranceReconciliationReference", request.getReference());
        payment.put("insuranceReconciliationNote", request.getNote());
        payload.setPayment(payment);
        BookingHistory.appendHistory(payload, actor, "Đối soát bảo hiểm thu hộ, hoàn khách " + request.getCustomerRefundAmount());
        savePayload(booking, payload);
        bookingRepository.save(booking);
    }

    // =========================================================================
    // CRUD CƠ BẢN
    // =========================================================================

    @Override
    public List<Payment> findAll() {
        return paymentRepository.findAll();
    }

    @Override
    public Optional<Payment> findById(Integer id) {
        return paymentRepository.findById(id);
    }

    @Override
    public Payment save(Payment entity) {
        return paymentRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        paymentRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC DEV 2: TÀI CHÍNH & THANH TOÁN
    // =========================================================================

    private String hotlineSuffix() {
        return systemConfigService == null ? "" : " " + systemConfigService.getConfig("HOTLINE").getConfigValue();
    }

    /**
     * 1. Khách hủy đơn của mình (mất cọc). Quy tắc nằm ở CustomerBookingRules.
     */
    @Override
    @Transactional
    public CancelWithRefundResponseDto cancelWithRefundPolicy(String bookingIdentifier, CancelBookingRequestDto request, String username) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        requireOwner(booking, username);
        List<Payment> existingPayments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        boolean balancePaid = existingPayments.stream()
                .anyMatch(p -> "BALANCE".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()));
        if (balancePaid) {
            throw new IllegalStateException("Đơn đã thanh toán đủ nên không tự hủy được. Vui lòng liên hệ hotline" + hotlineSuffix() + " để được hỗ trợ.");
        }
        boolean horsesPickedUp = "en_route_to_pickup".equals(booking.getStatus()) && bookingService.isPickupComplete(booking);
        if (horsesPickedUp) {
            throw new IllegalStateException("Kíp xe đã nhận ngựa nên không tự hủy đơn được. Vui lòng liên hệ nhà xe để được hỗ trợ hủy chuyến và nhận lại ngựa.");
        }
        if (!CustomerBookingRules.canCancel(booking.getStatus(), false, false)) {
            throw new IllegalStateException("Không thể hủy đơn hàng ở trạng thái: " + booking.getStatus());
        }

        Optional<Payment> depositOpt = existingPayments.stream()
                .filter(p -> "DEPOSIT".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()))
                .findFirst();

        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Khách hàng hủy đơn";
        String actor = username;

        // Trường hợp 1: Chưa cọc -> Hủy miễn phí, không mất tiền
        if (depositOpt.isEmpty()) {
            boolean withdrawal = CustomerBookingRules.isBeforeDeposit(booking.getStatus());
            booking.setStatus("cancelled");
            BookingDetailsPayloadDto payload = extractPayload(booking);
            BookingHistory.appendHistory(payload, actor, withdrawal ? "Rút yêu cầu vận chuyển" : "Hủy đơn");
            savePayload(booking, payload);
            bookingRepository.save(booking);
            staffNotifier.toManagers(booking, "Khách hủy đơn · " + booking.getCode(),
                    "Khách hủy đơn trước khi đặt cọc" + (reason == null || reason.isBlank() ? "." : ": " + reason), "/manager/cancelled-orders");
            staffNotifier.bookingCancelled(booking, payload, reason);

            return CancelWithRefundResponseDto.builder()
                    .bookingCode(booking.getCode())
                    .bookingStatus("cancelled")
                    .depositPaid(BigDecimal.ZERO)
                    .daysToDeparture(0)
                    .refundPercentage(0)
                    .refundAmount(BigDecimal.ZERO)
                    .penaltyAmount(BigDecimal.ZERO)
                    .message("Hủy đơn thành công trước khi đặt cọc. Không phát sinh chi phí phạt.")
                    .build();
        }

        // Trường hợp 2: Đã cọc -> Khách tự ý hủy đơn: Mất 100% tiền cọc (không hoàn tiền)
        CancelWithRefundResponseDto forfeited = forfeitDeposit(booking, depositOpt.get(), reason, actor, "Hủy đơn sau khi đặt cọc",
                "Khách hàng tự ý hủy đơn sau khi đặt cọc.");
        staffNotifier.toManagers(booking, "Khách hủy đơn đã cọc · " + booking.getCode(),
                "Khách hủy sau khi đặt cọc, tiền cọc không hoàn. Kiểm tra khoản bảo hiểm thu hộ cần đối soát.", "/manager/cancelled-orders");
        return forfeited;
    }

    /**
     * Quản lý hủy đơn "Không nhận được ngựa" (pickup_failed): kíp xe đã tới điểm đón nhưng không còn ngựa nào khớp hồ sơ.
     * Khách chưa trả 70% còn lại (chỉ trả sau khi nhận ngựa xong) nên mất phần cọc vận chuyển, không có khoản nào phải hoàn.
     */
    @Override
    @Transactional
    public CancelWithRefundResponseDto cancelPickupFailed(String bookingIdentifier, String reason, String username) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
        if (!"pickup_failed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ hủy được đơn đang ở trạng thái Không nhận được ngựa.");
        }
        List<Payment> existingPayments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        if (existingPayments.stream().anyMatch(p -> "BALANCE".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()))) {
            throw new IllegalStateException("Đơn đã thu 70% còn lại, cần hoàn tiền cho khách trước khi hủy.");
        }
        Payment deposit = existingPayments.stream()
                .filter(p -> "DEPOSIT".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Đơn chưa có tiền cọc đã thanh toán."));
        String why = reason != null && !reason.isBlank() ? reason.trim() : "Không nhận được ngựa ở điểm đón";
        return forfeitDeposit(booking, deposit, why, username, "Hủy đơn vì không nhận được ngựa: " + why,
                "Không nhận được ngựa ở điểm đón (" + why + ").");
    }

    @Override
    @Transactional
    public CancelWithRefundResponseDto cancelTrip(String bookingIdentifier, String fault, String reason, String username) {
        Booking booking = bookingRepository.findByIdForUpdate(parseBookingId(bookingIdentifier))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
        if (!TRIP_CANCELLABLE_STATUSES.contains(booking.getStatus())) {
            throw new IllegalStateException("Chỉ hủy chuyến khi đơn đã đặt cọc và xe chưa rời điểm đón.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần nhập lý do hủy chuyến.");
        }
        List<Payment> payments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        Payment deposit = payments.stream()
                .filter(p -> "DEPOSIT".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Đơn chưa có tiền cọc đã thanh toán."));
        Payment balance = payments.stream()
                .filter(p -> "BALANCE".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .orElse(null);
        // Ngựa đã trên xe: đơn chờ kíp xe trả ngựa và hồ sơ gốc tại chỗ (có biên bản) rồi mới đóng hẳn
        boolean horsesOnBoard = "en_route_to_pickup".equals(booking.getStatus()) && bookingService.isPickupComplete(booking);
        String why = reason.trim();

        CancelWithRefundResponseDto response;
        if ("COMPANY".equalsIgnoreCase(fault)) {
            response = refundAll(booking, payments, why, username, horsesOnBoard);
        } else if ("CUSTOMER".equalsIgnoreCase(fault)) {
            // Khách đã trả 70% là đã đồng ý bắt đầu chuyến: hủy do khách thì không hoàn khoản nào
            response = forfeitDeposit(booking, deposit, balance, why, username, "Quản lý hủy chuyến do khách: " + why,
                    "Nhà xe hủy chuyến do phía khách (" + why + ").", horsesOnBoard);
        } else {
            throw new IllegalArgumentException("Cần chọn lỗi do công ty (COMPANY) hoặc do khách (CUSTOMER).");
        }
        String customerMessage = response.getMessage() + (horsesOnBoard
                ? " Kíp xe sẽ trả lại ngựa và hồ sơ gốc tại điểm đón, hai bên ký biên bản trả ngựa." : "");
        response.setMessage(customerMessage);
        staffNotifier.toCustomer(booking, "Nhà xe đã hủy chuyến · " + booking.getCode(), customerMessage);
        return response;
    }

    // Ngựa đã trên xe thì đơn sang "Chờ trả ngựa" và kíp xe được giao việc trả; chưa có ngựa thì đóng đơn ngay
    private void closeCancelledBooking(Booking booking, BookingDetailsPayloadDto payload, String reason, boolean horsesOnBoard) {
        if (horsesOnBoard) {
            booking.setStatus("returning_horses");
            BookingHistory.appendHistory(payload, "Hệ thống", "Chờ kíp xe trả ngựa và hồ sơ gốc tại điểm đón");
        } else {
            booking.setStatus("cancelled");
        }
        savePayload(booking, payload);
        bookingRepository.save(booking);
        if (horsesOnBoard) {
            staffNotifier.horsesToReturn(booking, payload, reason);
        } else {
            staffNotifier.bookingCancelled(booking, payload, reason);
        }
    }

    // Công ty hủy chuyến: hoàn toàn bộ tiền khách đã trả (cọc, 70% nếu có, gồm cả bảo hiểm thu hộ).
    // Hệ thống ghi nhận khoản hoàn; việc chuyển khoản trả khách làm ngoài hệ thống như đối soát bảo hiểm.
    private CancelWithRefundResponseDto refundAll(Booking booking, List<Payment> payments, String reason, String actor, boolean horsesOnBoard) {
        Instant now = Instant.now();
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal depositPaid = BigDecimal.ZERO;
        for (Payment p : payments) {
            if (!"COMPLETED".equalsIgnoreCase(p.getStatus()) || p.getAmount() == null) continue;
            if ("DEPOSIT".equalsIgnoreCase(p.getPaymentType())) depositPaid = depositPaid.add(p.getAmount());
            if ("DEPOSIT".equalsIgnoreCase(p.getPaymentType()) || "BALANCE".equalsIgnoreCase(p.getPaymentType())) {
                collected = collected.add(p.getAmount());
            } else if ("REFUND".equalsIgnoreCase(p.getPaymentType())) {
                collected = collected.subtract(p.getAmount());
            }
        }
        collected = collected.max(BigDecimal.ZERO);
        Payment refund = null;
        if (collected.signum() > 0) {
            refund = paymentRepository.save(Payment.builder()
                    .bookingID(booking)
                    .amount(collected)
                    .paymentDate(now)
                    .paymentType("REFUND")
                    .paymentMethod("MANUAL")
                    .transactionCode("RF-" + booking.getId() + "-" + now.toEpochMilli())
                    .status("COMPLETED")
                    .note("Hoàn 100% do nhà xe hủy chuyến: " + reason)
                    .build());
        }

        BookingDetailsPayloadDto payload = extractPayload(booking);
        Map<String, Object> paymentMap = getOrCreatePaymentMap(payload);
        paymentMap.put("cancelled", true);
        paymentMap.put("cancelledBy", "COMPANY");
        paymentMap.put("refundAmount", collected);
        paymentMap.put("penaltyAmount", BigDecimal.ZERO);
        paymentMap.put("insuranceReconciliationPending", false);
        paymentMap.put("cancelReason", reason);
        paymentMap.put("cancelledAt", now.toEpochMilli());
        payload.setPayment(paymentMap);
        BookingHistory.appendHistory(payload, actor, "Nhà xe hủy chuyến, hoàn 100% (" + collected + " VND): " + reason);
        closeCancelledBooking(booking, payload, reason, horsesOnBoard);

        String message = "Nhà xe hủy chuyến (" + reason + "). Hoàn lại toàn bộ " + collected
                + " VND đã thanh toán. Nhà xe sẽ chuyển khoản và liên hệ xác nhận.";
        return CancelWithRefundResponseDto.builder()
                .bookingCode(booking.getCode())
                .bookingStatus(booking.getStatus())
                .depositPaid(depositPaid)
                .daysToDeparture(booking.getDepartureDate() == null ? 0 : ChronoUnit.DAYS.between(now, booking.getDepartureDate()))
                .refundPercentage(100)
                .refundAmount(collected)
                .penaltyAmount(BigDecimal.ZERO)
                .message(message)
                .refundPayment(refund != null ? mapToPaymentResponse(refund, booking) : null)
                .build();
    }

    // Hủy đơn đã đặt cọc mà chưa trả 70%: khách mất phần cọc vận chuyển, phần bảo hiểm thu hộ chờ quản lý đối soát riêng
    private CancelWithRefundResponseDto forfeitDeposit(Booking booking, Payment deposit, String reason, String actor,
            String historyText, String lead) {
        return forfeitDeposit(booking, deposit, null, reason, actor, historyText, lead, false);
    }

    // balance khác null: khách đã trả 70% (đã đồng ý bắt đầu chuyến) nên mất cả khoản 70%, không hoàn
    private CancelWithRefundResponseDto forfeitDeposit(Booking booking, Payment deposit, Payment balance, String reason, String actor,
            String historyText, String lead, boolean horsesOnBoard) {
        BigDecimal depositPaid = deposit.getAmount();
        BigDecimal balancePaid = balance != null && balance.getAmount() != null ? balance.getAmount() : BigDecimal.ZERO;
        Instant now = Instant.now();
        long daysToDeparture = 0;
        if (booking.getDepartureDate() != null) {
            daysToDeparture = ChronoUnit.DAYS.between(now, booking.getDepartureDate());
        }

        int refundPercentage = 0;
        BigDecimal refundAmount = BigDecimal.ZERO;
        BookingDetailsPayloadDto cancellationPayload = extractPayload(booking);
        BigDecimal insuranceCollected = HorseInsurancePolicy.insuranceDeposit(cancellationPayload.getQuote(), depositPaid)
                .add(HorseInsurancePolicy.insuranceDeposit(cancellationPayload.getQuote(), balancePaid));
        BigDecimal penaltyAmount = depositPaid.add(balancePaid).subtract(insuranceCollected);
        String policyMsg;
        if (balancePaid.signum() > 0) {
            policyMsg = lead + " Khách đã thanh toán 70% còn lại, tức đã đồng ý bắt đầu chuyến, nên không hoàn tiền. Phạt "
                    + penaltyAmount + " VND (cọc và 70% phần vận chuyển).";
            if (insuranceCollected.signum() > 0) policyMsg += " Phần bảo hiểm thu hộ " + insuranceCollected + " VND đang chờ quản lý đối soát riêng.";
        } else if (insuranceCollected.signum() > 0) {
            policyMsg = "Phạt phần cọc vận chuyển: " + penaltyAmount + " VND. Phần bảo hiểm thu hộ " + insuranceCollected + " VND đang chờ quản lý đối soát riêng.";
        } else {
            policyMsg = lead + " Không áp dụng hoàn cọc. Toàn bộ tiền cọc (" + penaltyAmount + " VND) tính vào phí phạt giữ chỗ và chuẩn bị phương tiện.";
        }
        Payment refundPayment = null;

        // Ghi nhận phí phạt hủy (PENALTY): khấu trừ 100% tiền cọc (và 70% nếu khách đã trả)
        Payment penaltyPayment = Payment.builder()
                .bookingID(booking)
                .amount(penaltyAmount)
                .paymentDate(now)
                .paymentType("PENALTY")
                .paymentMethod("DEDUCTION")
                .transactionCode("PEN-" + booking.getId() + "-" + System.currentTimeMillis())
                .status("COMPLETED")
                .note((balancePaid.signum() > 0 ? "Phí phạt hủy phần vận chuyển của cọc và 70% còn lại: " : "Phí phạt hủy phần cọc vận chuyển: ")
                        + penaltyAmount + " VND")
                .build();
        paymentRepository.save(penaltyPayment);

        BookingDetailsPayloadDto payload = extractPayload(booking);
        Map<String, Object> paymentMap = getOrCreatePaymentMap(payload);
        paymentMap.put("insuranceCollected", insuranceCollected);
        paymentMap.put("insuranceReconciliationPending", insuranceCollected.signum() > 0);
        paymentMap.put("cancelled", true);
        paymentMap.put("refundAmount", refundAmount);
        paymentMap.put("penaltyAmount", penaltyAmount);
        paymentMap.put("cancelReason", reason);
        paymentMap.put("cancelledAt", now.toEpochMilli()); // khách xem lại thời điểm hủy trong lịch sử đơn
        payload.setPayment(paymentMap);

        BookingHistory.appendHistory(payload, actor, historyText);
        closeCancelledBooking(booking, payload, reason, horsesOnBoard);

        log.info("Cancelled booking ID={} with policy: refund={}, penalty={}", booking.getId(), refundAmount, penaltyAmount);

        return CancelWithRefundResponseDto.builder()
                .bookingCode(booking.getCode())
                .bookingStatus(booking.getStatus())
                .depositPaid(depositPaid)
                .daysToDeparture(daysToDeparture)
                .refundPercentage(refundPercentage)
                .refundAmount(refundAmount)
                .penaltyAmount(penaltyAmount)
                .message(policyMsg)
                .refundPayment(refundPayment != null ? mapToPaymentResponse(refundPayment, booking) : null)
                .build();
    }

    /**
     * 2. Xem tóm tắt tài chính của đơn hàng
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentSummaryResponseDto getPaymentSummary(String bookingIdentifier, String customerUsername) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
        if (customerUsername != null) {
            requireOwner(booking, customerUsername);
        }

        Quotation quotation = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId()).orElse(null);
        List<Payment> payments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());

        BigDecimal totalAmount = quotation != null && quotation.getTotalAmount() != null
                ? quotation.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal depositAmount = quotation != null && quotation.getDepositAmount() != null
                ? quotation.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal balanceAmount = quotation != null && quotation.getBalanceAmount() != null
                ? quotation.getBalanceAmount() : BigDecimal.ZERO;

        BigDecimal totalPaid = BigDecimal.ZERO;
        boolean depositPaid = false;
        boolean balancePaid = false;

        List<PaymentResponseDto> paymentResponses = new ArrayList<>();
        for (Payment p : payments) {
            paymentResponses.add(mapToPaymentResponse(p, booking));
            if ("COMPLETED".equalsIgnoreCase(p.getStatus())) {
                if ("DEPOSIT".equalsIgnoreCase(p.getPaymentType())) {
                    depositPaid = true;
                    totalPaid = totalPaid.add(p.getAmount());
                } else if ("BALANCE".equalsIgnoreCase(p.getPaymentType())) {
                    balancePaid = true;
                    totalPaid = totalPaid.add(p.getAmount());
                } else if ("REFUND".equalsIgnoreCase(p.getPaymentType())) {
                    totalPaid = totalPaid.subtract(p.getAmount());
                } else if ("SETTLEMENT".equalsIgnoreCase(p.getPaymentType())) {
                    totalPaid = totalPaid.add(p.getAmount());
                }
            }
        }

        BigDecimal remainingBalance = totalAmount.subtract(totalPaid);
        if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) {
            remainingBalance = BigDecimal.ZERO;
        }

        if (Set.of("cancelled", "returning_horses").contains(booking.getStatus())) remainingBalance = BigDecimal.ZERO;
        boolean fullyPaid = depositPaid && balancePaid;

        return PaymentSummaryResponseDto.builder()
                .bookingCode(booking.getCode())
                .waybillNo(booking.getWaybillNo())
                .bookingStatus(booking.getStatus())
                .totalAmount(totalAmount)
                .depositAmount(depositAmount)
                .balanceAmount(balanceAmount)
                .totalPaid(totalPaid)
                .remainingBalance(remainingBalance)
                .depositPaid(depositPaid)
                .balancePaid(balancePaid)
                .fullyPaid(fullyPaid)
                .payments(paymentResponses)
                .build();
    }

    /**
     * 3. Lấy danh sách lịch sử tất cả các đợt thanh toán của một đơn
     */
    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getPaymentsByBooking(String bookingIdentifier, String customerUsername) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
        if (customerUsername != null) {
            requireOwner(booking, customerUsername);
        }

        List<Payment> payments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        return payments.stream()
                .map(p -> mapToPaymentResponse(p, booking))
                .collect(Collectors.toList());
    }

    /**
     * 4. Lấy chi tiết một giao dịch thanh toán
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentDetail(Integer paymentId, String customerUsername) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch thanh toán ID: " + paymentId));
        if (customerUsername != null) {
            requireOwner(payment.getBookingID(), customerUsername);
        }
        return mapToPaymentResponse(payment, payment.getBookingID());
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private void requireOwner(Booking booking, String username) {
        User owner = booking != null && booking.getCustomerID() != null ? booking.getCustomerID().getUser() : null;
        boolean ownsBooking = owner != null && username != null
                && (username.equals(owner.getUsername()) || username.equals(owner.getEmail()));
        if (!ownsBooking) {
            throw new AccessDeniedException("Bạn không có quyền với đơn hàng này.");
        }
    }

    private PaymentResponseDto mapToPaymentResponse(Payment payment, Booking booking) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .bookingCode(booking != null ? booking.getCode() : null)
                .waybillNo(booking != null ? booking.getWaybillNo() : null)
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentType(payment.getPaymentType())
                .paymentMethod(payment.getPaymentMethod())
                .transactionCode(payment.getTransactionCode())
                .status(payment.getStatus())
                .note(payment.getNote())
                .build();
    }

    private Integer parseBookingId(String bookingIdentifier) {
        if (bookingIdentifier == null || bookingIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }
        return bookingRepository.findIdByCode(bookingIdentifier.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
    }

    private BookingDetailsPayloadDto extractPayload(Booking booking) {
        return BookingDetailsJson.read(objectMapper, booking);
    }

    private void savePayload(Booking booking, BookingDetailsPayloadDto payload) {
        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("Could not serialize payload for booking {}: {}", booking.getId(), ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreatePaymentMap(BookingDetailsPayloadDto payload) {
        if (payload.getPayment() instanceof Map) {
            return (Map<String, Object>) payload.getPayment();
        }
        Map<String, Object> map = new HashMap<>();
        payload.setPayment(map);
        return map;
    }

}
