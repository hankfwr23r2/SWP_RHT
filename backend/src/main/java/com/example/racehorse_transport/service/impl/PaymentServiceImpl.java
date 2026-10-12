package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.payment.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.PaymentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final QuotationRepository quotationRepository;
    private final ObjectMapper objectMapper;

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

    /**
     * 1. Khách hàng thanh toán đặt cọc 30% để nhận Vận đơn (Waybill)
     */
    @Override
    @Transactional
    public PaymentResponse payDeposit(ProcessPaymentRequest request, String username) {
        Integer bookingId = parseBookingId(request.getBookingId());
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + request.getBookingId()));

        if (!"awaiting_payment".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở trạng thái chờ thanh toán cọc! Trạng thái hiện tại: " + booking.getStatus());
        }

        Quotation quotation = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy thông tin báo giá của đơn hàng!"));

        Instant now = Instant.now();
        if (quotation.getExpiresAt() != null && now.isAfter(quotation.getExpiresAt())) {
            throw new IllegalStateException("Báo giá đã hết hạn 48 giờ! Vui lòng liên hệ Quản lý để cấp lại báo giá.");
        }

        BigDecimal payAmount = (request.getAmount() != null)
                ? request.getAmount()
                : quotation.getDepositAmount();

        String paymentMethod = (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank())
                ? request.getPaymentMethod()
                : "BANK_TRANSFER";

        String transactionCode = (request.getTransactionCode() != null && !request.getTransactionCode().isBlank())
                ? request.getTransactionCode()
                : "DEP-" + String.format("%04d", booking.getId()) + "-" + System.currentTimeMillis();

        Payment payment = Payment.builder()
                .bookingID(booking)
                .amount(payAmount)
                .paymentDate(now)
                .paymentType("DEPOSIT")
                .paymentMethod(paymentMethod)
                .transactionCode(transactionCode)
                .status("COMPLETED")
                .note(request.getNote() != null ? request.getNote() : "Thanh toán cọc 30% cho đơn BK-" + booking.getId())
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        quotation.setStatus("ACCEPTED");
        quotationRepository.save(quotation);

        String waybillNo = "VD-2026-" + String.format("%04d", booking.getId());
        booking.setWaybillNo(waybillNo);
        booking.setStatus("waybill_issued");

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> paymentMap = getOrCreatePaymentMap(payload);
        paymentMap.put("depositPaid", true);
        paymentMap.put("depositAmount", payAmount);
        paymentMap.put("depositPaidAt", now.toEpochMilli());
        paymentMap.put("depositTransaction", transactionCode);
        paymentMap.put("depositMethod", paymentMethod);
        payload.setPayment(paymentMap);

        appendHistory(payload, username != null ? username : "Khách hàng",
                "Thanh toán cọc 30% (" + payAmount + " VND), cấp Vận đơn " + waybillNo);
        savePayload(booking, payload);
        bookingRepository.save(booking);

        log.info("Successfully processed DEPOSIT payment ID={} for booking ID={}, waybill={}",
                savedPayment.getId(), booking.getId(), waybillNo);

        return mapToPaymentResponse(savedPayment, booking);
    }

    /**
     * 2. Khách hàng thanh toán 70% còn lại trước giờ khởi hành (ngày D - Flow 4)
     */
    @Override
    @Transactional
    public PaymentResponse payBalance(ProcessPaymentRequest request, String username) {
        Integer bookingId = parseBookingId(request.getBookingId());
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + request.getBookingId()));

        // Kiểm tra đơn đã cọc chưa
        List<Payment> existingPayments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        boolean hasDeposit = existingPayments.stream()
                .anyMatch(p -> "DEPOSIT".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()));
        if (!hasDeposit) {
            throw new IllegalStateException("Đơn hàng chưa thanh toán tiền cọc 30%! Vui lòng cọc trước khi thanh toán số dư.");
        }

        // Kiểm tra xem đã thanh toán 70% chưa
        boolean alreadyPaidBalance = existingPayments.stream()
                .anyMatch(p -> "BALANCE".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()));
        if (alreadyPaidBalance) {
            throw new IllegalStateException("Đơn hàng này đã thanh toán đủ 70% còn lại!");
        }

        Quotation quotation = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy thông tin báo giá của đơn hàng!"));

        Instant now = Instant.now();
        // Kiểm tra báo giá còn hạn (đồng nhất với payDeposit)
        if (quotation.getExpiresAt() != null && now.isAfter(quotation.getExpiresAt())) {
            throw new IllegalStateException("Báo giá đã hết hạn 48 giờ! Vui lòng liên hệ Quản lý để cấp lại báo giá.");
        }

        BigDecimal payAmount = (request.getAmount() != null)
                ? request.getAmount()
                : quotation.getBalanceAmount();

        String paymentMethod = (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank())
                ? request.getPaymentMethod()
                : "BANK_TRANSFER";

        String transactionCode = (request.getTransactionCode() != null && !request.getTransactionCode().isBlank())
                ? request.getTransactionCode()
                : "BAL-" + String.format("%04d", booking.getId()) + "-" + System.currentTimeMillis();

        Payment payment = Payment.builder()
                .bookingID(booking)
                .amount(payAmount)
                .paymentDate(now)
                .paymentType("BALANCE")
                .paymentMethod(paymentMethod)
                .transactionCode(transactionCode)
                .status("COMPLETED")
                .note(request.getNote() != null ? request.getNote() : "Thanh toán 70% cước còn lại ngày D cho đơn BK-" + booking.getId())
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> paymentMap = getOrCreatePaymentMap(payload);
        paymentMap.put("balancePaid", true);
        paymentMap.put("balanceAmount", payAmount);
        paymentMap.put("balancePaidAt", now.toEpochMilli());
        paymentMap.put("balanceTransaction", transactionCode);
        paymentMap.put("balanceMethod", paymentMethod);
        paymentMap.put("isFullyPaidFixedRate", true);
        payload.setPayment(paymentMap);

        appendHistory(payload, username != null ? username : "Khách hàng",
                "Thanh toán 70% còn lại (" + payAmount + " VND). Đã đủ điều kiện để xe bắt đầu hành trình.");
        savePayload(booking, payload);
        bookingRepository.save(booking);

        log.info("Successfully processed BALANCE payment ID={} for booking ID={}", savedPayment.getId(), booking.getId());
        return mapToPaymentResponse(savedPayment, booking);
    }

    /**
     * 3. Hủy đơn hàng và áp dụng chính sách hoàn cọc / phạt cọc (PRD mục 8.2 & 8.3)
     */
    @Override
    @Transactional
    public CancelWithRefundResponse cancelWithRefundPolicy(String bookingIdentifier, CancelBookingRequest request, String username) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        if ("cancelled".equalsIgnoreCase(booking.getStatus()) || "completed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Không thể hủy đơn hàng ở trạng thái: " + booking.getStatus());
        }

        List<Payment> existingPayments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        Optional<Payment> depositOpt = existingPayments.stream()
                .filter(p -> "DEPOSIT".equalsIgnoreCase(p.getPaymentType()) && "COMPLETED".equalsIgnoreCase(p.getStatus()))
                .findFirst();

        Instant now = Instant.now();
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Khách hàng hủy đơn";
        String actor = (request != null && request.getCancelledBy() != null) ? request.getCancelledBy() : (username != null ? username : "Khách hàng");

        // Trường hợp 1: Chưa cọc -> Hủy miễn phí, không mất tiền
        if (depositOpt.isEmpty()) {
            booking.setStatus("cancelled");
            BookingDetailsPayload payload = extractPayload(booking);
            appendHistory(payload, actor, "Hủy đơn trước khi đặt cọc (Lý do: " + reason + ")");
            savePayload(booking, payload);
            bookingRepository.save(booking);

            return CancelWithRefundResponse.builder()
                    .bookingId(booking.getId())
                    .bookingCode("BK-" + booking.getId())
                    .bookingStatus("cancelled")
                    .depositPaid(BigDecimal.ZERO)
                    .daysToDeparture(0)
                    .refundPercentage(0)
                    .refundAmount(BigDecimal.ZERO)
                    .penaltyAmount(BigDecimal.ZERO)
                    .message("Hủy đơn thành công trước khi đặt cọc. Không phát sinh chi phí phạt.")
                    .build();
        }

        // Trường hợp 2: Đã cọc -> Tính toán theo lead-time ngày khởi hành D
        BigDecimal depositPaid = depositOpt.get().getAmount();
        long daysToDeparture = 0;
        if (booking.getDepartureDate() != null) {
            daysToDeparture = ChronoUnit.DAYS.between(now, booking.getDepartureDate());
        }

        int refundPercentage;
        BigDecimal refundAmount;
        BigDecimal penaltyAmount;
        String policyMsg;

        if (daysToDeparture >= 15) {
            // Hủy trước ngày D >= 15 ngày: hoàn 50% cọc, nhà xe giữ 50% cọc làm phí hủy
            refundPercentage = 50;
            refundAmount = depositPaid.multiply(BigDecimal.valueOf(0.5));
            penaltyAmount = depositPaid.subtract(refundAmount);
            policyMsg = "Hủy đơn trước D-15 ngày (" + daysToDeparture + " ngày). Hoàn 50% tiền cọc (" + refundAmount + " VND). Phí phạt hủy: " + penaltyAmount + " VND.";
        } else {
            // Hủy trước ngày D < 15 ngày: không hoàn cọc (100% tiền cọc tính vào phí phạt)
            refundPercentage = 0;
            refundAmount = BigDecimal.ZERO;
            penaltyAmount = depositPaid;
            policyMsg = "Hủy đơn cận ngày D (" + daysToDeparture + " ngày < 15 ngày). Không áp dụng hoàn cọc. Toàn bộ tiền cọc (" + penaltyAmount + " VND) tính vào phí phạt hủy.";
        }

        // Ghi nhận dòng tiền hoàn (REFUND) nếu có
        Payment refundPayment = null;
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            refundPayment = Payment.builder()
                    .bookingID(booking)
                    .amount(refundAmount)
                    .paymentDate(now)
                    .paymentType("REFUND")
                    .paymentMethod("BANK_TRANSFER")
                    .transactionCode("REF-" + booking.getId() + "-" + System.currentTimeMillis())
                    .status("COMPLETED")
                    .note("Hoàn lại 50% tiền cọc do hủy đơn trước 15 ngày: " + refundAmount + " VND")
                    .build();
            paymentRepository.save(refundPayment);
        }

        // Ghi nhận phí phạt hủy (PENALTY)
        Payment penaltyPayment = Payment.builder()
                .bookingID(booking)
                .amount(penaltyAmount)
                .paymentDate(now)
                .paymentType("PENALTY")
                .paymentMethod("DEDUCTION")
                .transactionCode("PEN-" + booking.getId() + "-" + System.currentTimeMillis())
                .status("COMPLETED")
                .note("Phí phạt hủy đơn: " + penaltyAmount + " VND")
                .build();
        paymentRepository.save(penaltyPayment);

        booking.setStatus("cancelled");
        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> paymentMap = getOrCreatePaymentMap(payload);
        paymentMap.put("cancelled", true);
        paymentMap.put("refundAmount", refundAmount);
        paymentMap.put("penaltyAmount", penaltyAmount);
        paymentMap.put("cancelReason", reason);
        payload.setPayment(paymentMap);

        appendHistory(payload, actor, policyMsg + " Lý do: " + reason);
        savePayload(booking, payload);
        bookingRepository.save(booking);

        log.info("Cancelled booking ID={} with policy: refund={}, penalty={}", booking.getId(), refundAmount, penaltyAmount);

        return CancelWithRefundResponse.builder()
                .bookingId(booking.getId())
                .bookingCode("BK-" + booking.getId())
                .bookingStatus("cancelled")
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
     * 4. Xem tóm tắt tài chính của đơn hàng
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentSummaryResponse getPaymentSummary(String bookingIdentifier) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

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

        List<PaymentResponse> paymentResponses = new ArrayList<>();
        for (Payment p : payments) {
            paymentResponses.add(mapToPaymentResponse(p, booking));
            if ("COMPLETED".equalsIgnoreCase(p.getStatus())) {
                if ("DEPOSIT".equalsIgnoreCase(p.getPaymentType())) {
                    depositPaid = true;
                    totalPaid = totalPaid.add(p.getAmount());
                } else if ("BALANCE".equalsIgnoreCase(p.getPaymentType())) {
                    balancePaid = true;
                    totalPaid = totalPaid.add(p.getAmount());
                } else if ("SETTLEMENT".equalsIgnoreCase(p.getPaymentType())) {
                    totalPaid = totalPaid.add(p.getAmount());
                }
            }
        }

        BigDecimal remainingBalance = totalAmount.subtract(totalPaid);
        if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) {
            remainingBalance = BigDecimal.ZERO;
        }

        boolean fullyPaid = depositPaid && balancePaid;

        return PaymentSummaryResponse.builder()
                .bookingId(booking.getId())
                .bookingCode("BK-" + booking.getId())
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
     * 5. Lấy danh sách lịch sử tất cả các đợt thanh toán của một đơn
     */
    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByBooking(String bookingIdentifier) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        List<Payment> payments = paymentRepository.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        return payments.stream()
                .map(p -> mapToPaymentResponse(p, booking))
                .collect(Collectors.toList());
    }

    /**
     * 6. Lấy chi tiết một giao dịch thanh toán
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentDetail(Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch thanh toán ID: " + paymentId));
        return mapToPaymentResponse(payment, payment.getBookingID());
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private PaymentResponse mapToPaymentResponse(Payment payment, Booking booking) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .bookingId(booking != null ? booking.getId() : null)
                .bookingCode(booking != null ? "BK-" + booking.getId() : null)
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
        String cleanId = bookingIdentifier.trim();
        try {
            if (cleanId.toUpperCase().startsWith("BK-")) {
                return Integer.parseInt(cleanId.substring(3).trim());
            } else if (cleanId.toUpperCase().startsWith("ORD-")) {
                String[] parts = cleanId.split("-");
                return Integer.parseInt(parts[parts.length - 1].trim());
            } else {
                return Integer.parseInt(cleanId);
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Định dạng mã đơn không hợp lệ: " + bookingIdentifier);
        }
    }

    private BookingDetailsPayload extractPayload(Booking booking) {
        BookingDetailsPayload payload = new BookingDetailsPayload();
        if (booking.getDetailsJson() != null && !booking.getDetailsJson().trim().isEmpty()) {
            String json = booking.getDetailsJson().trim();
            try {
                if (json.startsWith("{")) {
                    payload = objectMapper.readValue(json, BookingDetailsPayload.class);
                }
            } catch (Exception ex) {
                log.warn("Error reading detailsJson for booking {}: {}", booking.getId(), ex.getMessage());
            }
        }
        return payload;
    }

    private void savePayload(Booking booking, BookingDetailsPayload payload) {
        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("Could not serialize payload for booking {}: {}", booking.getId(), ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreatePaymentMap(BookingDetailsPayload payload) {
        if (payload.getPayment() instanceof Map) {
            return (Map<String, Object>) payload.getPayment();
        }
        Map<String, Object> map = new HashMap<>();
        payload.setPayment(map);
        return map;
    }

    private void appendHistory(BookingDetailsPayload payload, String actor, String text) {
        if (payload.getHistory() == null) {
            payload.setHistory(new ArrayList<>());
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("time", System.currentTimeMillis());
        entry.put("actor", actor);
        entry.put("text", text);
        payload.getHistory().add(entry);
    }
}
