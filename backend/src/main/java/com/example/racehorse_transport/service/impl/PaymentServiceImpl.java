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
     * 1. Hủy đơn hàng và áp dụng chính sách hoàn cọc / phạt cọc (PRD mục 8.2 & 8.3)
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

        // Trường hợp 2: Đã cọc -> Khách tự ý hủy đơn: Mất 100% tiền cọc (không hoàn tiền)
        BigDecimal depositPaid = depositOpt.get().getAmount();
        long daysToDeparture = 0;
        if (booking.getDepartureDate() != null) {
            daysToDeparture = ChronoUnit.DAYS.between(now, booking.getDepartureDate());
        }

        int refundPercentage = 0;
        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal penaltyAmount = depositPaid;
        String policyMsg = "Khách hàng tự ý hủy đơn sau khi đặt cọc. Không áp dụng hoàn cọc. Toàn bộ tiền cọc (" + penaltyAmount + " VND) tính vào phí phạt giữ chỗ và chuẩn bị phương tiện.";

        Payment refundPayment = null;

        // Ghi nhận phí phạt hủy (PENALTY): khấu trừ 100% tiền cọc
        Payment penaltyPayment = Payment.builder()
                .bookingID(booking)
                .amount(penaltyAmount)
                .paymentDate(now)
                .paymentType("PENALTY")
                .paymentMethod("DEDUCTION")
                .transactionCode("PEN-" + booking.getId() + "-" + System.currentTimeMillis())
                .status("COMPLETED")
                .note("Phí phạt hủy đơn (mất 100% tiền cọc): " + penaltyAmount + " VND")
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
     * 2. Xem tóm tắt tài chính của đơn hàng
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
     * 3. Lấy danh sách lịch sử tất cả các đợt thanh toán của một đơn
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
     * 4. Lấy chi tiết một giao dịch thanh toán
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
