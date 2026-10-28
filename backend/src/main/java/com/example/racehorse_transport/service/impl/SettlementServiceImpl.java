package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.settlement.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.SettlementService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.ClearanceDocumentPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final QuotationRepository quotationRepository;
    private final VehicleRepository vehicleRepository;
    private final StaffRepository staffRepository;
    private final ObjectMapper objectMapper;

    /**
     * 1. Kê khai chi phí phát sinh thực tế có chứng từ (Evidence-First - Flow 6, bước 2)
     */
    @Override
    @Transactional
    public BookingResponse submitExpenses(String bookingIdentifier, SubmitExpensesRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        BookingDetailsPayload payload = extractPayload(booking);

        List<ExpenseItemDto> expenses = (request != null && request.getExpenses() != null)
                ? request.getExpenses()
                : Collections.emptyList();

        // Kiểm tra nguyên tắc Evidence-First: Nếu có chi phí thì bắt buộc phải có link ảnh chứng từ
        for (ExpenseItemDto item : expenses) {
            if (item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                if (item.getPhoto() == null || item.getPhoto().trim().isEmpty()) {
                    throw new IllegalArgumentException("Evidence-First: Khoản chi [" + item.getDescription() + "] bắt buộc phải có ảnh chụp hóa đơn/chứng từ!");
                }
            }
        }

        String actor = (request != null && request.getDriverName() != null && !request.getDriverName().isBlank())
                ? request.getDriverName()
                : (username != null ? username : "Tài xế");

        // Quy tắc PRD 7.1: Nếu không có chi phí nào phát sinh -> Hệ thống tự phát hành Bảng quyết toán 0 đồng
        if (expenses.isEmpty()) {
            long now = System.currentTimeMillis();
            long dueAt = now + (24 * 3600_000L); // 24 giờ sau khi phát hành

            Map<String, Object> settlementMap = new HashMap<>();
            settlementMap.put("items", Collections.emptyList());
            settlementMap.put("total", BigDecimal.ZERO);
            settlementMap.put("issuedAt", now);
            settlementMap.put("dueAt", dueAt);
            settlementMap.put("issuedBy", "Hệ thống");
            settlementMap.put("paid", null);
            payload.setSettlement(settlementMap);

            booking.setStatus("settlement_issued");
            appendHistory(payload, "Hệ thống", "Phát hành bảng quyết toán");
        } else {
            // Có chi phí phát sinh -> Ghi nhận và chuyển sang chờ quản lý đối soát
            Map<String, Object> settlementDraft = new HashMap<>();
            settlementDraft.put("items", expenses);
            settlementDraft.put("submittedAt", System.currentTimeMillis());
            settlementDraft.put("submittedBy", actor);
            payload.setSettlement(settlementDraft);

            booking.setStatus("expenses_submitted");
            appendHistory(payload, actor, "Kê khai chi phí phát sinh");
        }

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Submitted expenses for booking ID={}: status={}", saved.getId(), saved.getStatus());
        return mapToBookingResponse(saved);
    }

    /**
     * 2. Quản lý (Manager) đối soát chứng từ và phát hành Bảng quyết toán phát sinh (Flow 6, bước 3)
     */
    @Override
    @Transactional
    public SettlementResponse issueSettlement(String bookingIdentifier, IssueSettlementRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        BookingDetailsPayload payload = extractPayload(booking);

        List<ExpenseItemDto> items = new ArrayList<>();
        if (request != null && request.getItems() != null && !request.getItems().isEmpty()) {
            items.addAll(request.getItems());
        } else {
            // Lấy từ bảng kê chi phí đã lưu trong settlement draft nếu có
            Map<String, Object> existingDraft = getMapFromObject(payload.getSettlement());
            if (existingDraft != null && existingDraft.get("items") instanceof List) {
                try {
                    List<ExpenseItemDto> draftItems = objectMapper.convertValue(
                            existingDraft.get("items"),
                            new TypeReference<List<ExpenseItemDto>>() {});
                    if (draftItems != null) {
                        items.addAll(draftItems);
                    }
                } catch (Exception ex) {
                    log.warn("Could not parse existing draft settlement items: {}", ex.getMessage());
                }
            }
        }

        // Cập nhật bên chịu (payer) từ request.getPayers() nếu được truyền vào
        if (request != null && request.getPayers() != null && !request.getPayers().isEmpty()) {
            for (ExpenseItemDto it : items) {
                if (it.getId() != null && request.getPayers().containsKey(it.getId())) {
                    it.setPayer(request.getPayers().get(it.getId()));
                }
            }
        }

        // Tính tổng số tiền thuộc phần khách chịu (payer = "customer") theo quy định PRD 7.4 & 11.5
        BigDecimal customerTotal = BigDecimal.ZERO;
        for (ExpenseItemDto item : items) {
            if ("customer".equalsIgnoreCase(item.getPayer()) && item.getAmount() != null) {
                customerTotal = customerTotal.add(item.getAmount());
            }
        }

        // Trừ chiết khấu / điều chỉnh thương mại nếu có
        if (request != null && request.getAdjustment() != null) {
            customerTotal = customerTotal.subtract(request.getAdjustment());
            if (customerTotal.compareTo(BigDecimal.ZERO) < 0) {
                customerTotal = BigDecimal.ZERO;
            }
        }

        String managerName = (request != null && request.getManagerName() != null && !request.getManagerName().isBlank())
                ? request.getManagerName()
                : (username != null ? username : "Quản lý");

        long now = System.currentTimeMillis();
        long dueAt = now + (24 * 3600_000L); // Thời hạn 24 giờ theo PRD 7.4

        Map<String, Object> settlementMap = new HashMap<>();
        settlementMap.put("items", items);
        settlementMap.put("total", customerTotal);
        settlementMap.put("adjustment", request != null ? request.getAdjustment() : BigDecimal.ZERO);
        settlementMap.put("note", request != null ? request.getNote() : "");
        settlementMap.put("issuedAt", now);
        settlementMap.put("dueAt", dueAt);
        settlementMap.put("issuedBy", managerName);
        settlementMap.put("paid", null);
        payload.setSettlement(settlementMap);

        booking.setStatus("settlement_issued");

        appendHistory(payload, managerName, "Phát hành bảng quyết toán");

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Manager issued settlement for booking ID={}, total={}", saved.getId(), customerTotal);

        return buildSettlementResponse(saved, payload, settlementMap);
    }

    /**
     * 3. Khách hàng xác nhận Bảng quyết toán 0 đồng + gửi đánh giá -> Đóng đơn hàng (Flow 6, bước 4 & 5).
     * Có khoản phát sinh phải trả thì bắt buộc thanh toán qua VNPAY (xem confirmSettlementPaid).
     */
    @Override
    @Transactional
    public BookingResponse payAndComplete(String bookingIdentifier, PaySettlementRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        BookingDetailsPayload payload = extractPayload(booking);

        Map<String, Object> settlementMap = getMapFromObject(payload.getSettlement());
        if (settlementMap == null) {
            throw new IllegalStateException("Đơn hàng chưa được phát hành Bảng quyết toán!");
        }

        BigDecimal extraTotal = settlementTotal(settlementMap);
        if (extraTotal.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Bảng quyết toán có khoản phát sinh " + extraTotal
                    + " VND, vui lòng thanh toán qua VNPAY.");
        }

        Instant now = Instant.now();
        Map<String, Object> paidInfo = new HashMap<>();
        paidInfo.put("paidAt", now.toEpochMilli());
        paidInfo.put("amount", BigDecimal.ZERO);
        paidInfo.put("reference", "ZERO-SETTLEMENT");
        settlementMap.put("paid", paidInfo);
        payload.setSettlement(settlementMap);

        if (request != null && request.getRating() != null) {
            payload.setRating(toRatingMap(request.getRating(), now));
        }

        Booking saved = closeBooking(booking, payload, username != null ? username : "Khách hàng",
                "Hoàn tất quyết toán và đánh giá");
        return mapToBookingResponse(saved);
    }

    /**
     * Chuẩn bị thanh toán quyết toán qua VNPAY: kiểm tra trạng thái, giữ tạm đánh giá của khách,
     * trả về số tiền phát sinh khách phải trả.
     */
    @Override
    @Transactional
    public BigDecimal holdForVnpay(Booking booking, RatingDto rating) {
        String status = booking.getStatus() != null ? booking.getStatus().toLowerCase() : "";
        if (!status.equals("settlement_issued") && !status.equals("payment_overdue")) {
            throw new IllegalStateException("Đơn hàng không ở bước thanh toán quyết toán! Trạng thái hiện tại: " + booking.getStatus());
        }
        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> settlementMap = getMapFromObject(payload.getSettlement());
        if (settlementMap == null) {
            throw new IllegalStateException("Đơn hàng chưa được phát hành Bảng quyết toán!");
        }
        BigDecimal extraTotal = settlementTotal(settlementMap);
        if (extraTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Bảng quyết toán 0 đồng, không cần thanh toán qua VNPAY.");
        }
        // Đánh giá chỉ được ghi chính thức khi VNPAY xác nhận đã nhận tiền
        if (rating != null) {
            settlementMap.put("pendingRating", toRatingMap(rating, Instant.now()));
        } else {
            settlementMap.remove("pendingRating");
        }
        payload.setSettlement(settlementMap);
        savePayload(booking, payload);
        bookingRepository.save(booking);
        return extraTotal;
    }

    /**
     * Áp hiệu ứng sau khi VNPAY xác nhận đã nhận tiền quyết toán: ghi đánh giá, đóng đơn, nhả xe về AVAILABLE.
     */
    @Override
    @Transactional
    public Booking confirmSettlementPaid(Payment payment, String actor) {
        Booking booking = payment.getBookingID();
        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> settlementMap = getMapFromObject(payload.getSettlement());
        if (settlementMap == null) {
            settlementMap = new HashMap<>();
        }
        Instant paidAt = payment.getPaymentDate() != null ? payment.getPaymentDate() : Instant.now();

        Map<String, Object> paidInfo = new HashMap<>();
        paidInfo.put("paidAt", paidAt.toEpochMilli());
        paidInfo.put("amount", payment.getAmount());
        paidInfo.put("reference", payment.getTransactionCode());
        paidInfo.put("method", payment.getPaymentMethod());
        settlementMap.put("paid", paidInfo);

        Object pendingRating = settlementMap.remove("pendingRating");
        if (pendingRating instanceof Map) {
            payload.setRating(pendingRating);
        }
        payload.setSettlement(settlementMap);

        return closeBooking(booking, payload, actor != null ? actor : "Khách hàng",
                "Hoàn tất quyết toán và đánh giá");
    }

    /**
     * 4. Xem chi tiết Bảng quyết toán phát sinh của đơn hàng
     */
    @Override
    @Transactional(readOnly = true)
    public SettlementResponse getSettlementDetail(String bookingIdentifier) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> settlementMap = getMapFromObject(payload.getSettlement());
        return buildSettlementResponse(booking, payload, settlementMap != null ? settlementMap : Collections.emptyMap());
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private BigDecimal settlementTotal(Map<String, Object> settlementMap) {
        Object totalObj = settlementMap.get("total");
        return totalObj instanceof Number
                ? BigDecimal.valueOf(((Number) totalObj).doubleValue())
                : BigDecimal.ZERO;
    }

    private Map<String, Object> toRatingMap(RatingDto rating, Instant now) {
        Map<String, Object> ratingMap = new HashMap<>();
        ratingMap.put("stars", rating.getStars());
        ratingMap.put("comment", rating.getComment() != null ? rating.getComment() : rating.getOverallFeedback());
        ratingMap.put("submittedAt", now.toEpochMilli());
        return ratingMap;
    }

    // Đóng đơn hàng và giải phóng xe trong chuyến về AVAILABLE
    private Booking closeBooking(Booking booking, BookingDetailsPayload payload, String actor, String historyText) {
        booking.setStatus("completed");
        appendHistory(payload, actor, historyText);
        releaseResources(payload);
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Booking ID={} completed and closed successfully", saved.getId());
        return saved;
    }

    private void releaseResources(BookingDetailsPayload payload) {
        if (payload.getTrips() != null) {
            for (VehicleTripDto trip : payload.getTrips()) {
                if (trip.getVehicleId() != null) {
                    try {
                        String vid = trip.getVehicleId().trim();
                        Vehicle vehicle = null;
                        try {
                            int numId = Integer.parseInt(vid);
                            vehicle = vehicleRepository.findById(numId).orElse(null);
                        } catch (NumberFormatException ignored) {}

                        if (vehicle == null && vid.matches("(?i)(VH|XE)-\\d+")) {
                            try {
                                int numId = Integer.parseInt(vid.replaceAll("[^0-9]", ""));
                                vehicle = vehicleRepository.findById(numId).orElse(null);
                            } catch (NumberFormatException ignored) {}
                        }

                        if (vehicle == null) {
                            vehicle = vehicleRepository.findByLicensePlate(vid).orElse(null);
                        }

                        if (vehicle != null) {
                            vehicle.setStatus("AVAILABLE");
                            vehicleRepository.save(vehicle);
                            log.info("Released vehicle {} to AVAILABLE", vehicle.getVehicleCode());
                        }
                    } catch (Exception ex) {
                        log.warn("Could not release vehicle {}: {}", trip.getVehicleId(), ex.getMessage());
                    }
                }
            }
        }
    }

    private SettlementResponse buildSettlementResponse(Booking booking, BookingDetailsPayload payload, Map<String, Object> settlementMap) {
        Quotation quotation = quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId()).orElse(null);
        BigDecimal totalBasePaid = quotation != null && quotation.getTotalAmount() != null
                ? quotation.getTotalAmount() : BigDecimal.ZERO;

        BigDecimal extraTotal = BigDecimal.ZERO;
        Object totalObj = settlementMap.get("total");
        if (totalObj instanceof Number) {
            extraTotal = BigDecimal.valueOf(((Number) totalObj).doubleValue());
        }

        List<ExpenseItemDto> items = new ArrayList<>();
        Object itemsObj = settlementMap.get("items");
        if (itemsObj instanceof List) {
            try {
                items = objectMapper.convertValue(itemsObj, new TypeReference<List<ExpenseItemDto>>() {});
            } catch (Exception e) {
                log.warn("Could not convert settlement items: {}", e.getMessage());
            }
        }

        Long issuedAt = settlementMap.get("issuedAt") instanceof Number
                ? ((Number) settlementMap.get("issuedAt")).longValue() : null;
        Long dueAt = settlementMap.get("dueAt") instanceof Number
                ? ((Number) settlementMap.get("dueAt")).longValue() : null;
        String issuedBy = (String) settlementMap.get("issuedBy");
        Object paidInfo = settlementMap.get("paid");

        RatingDto rating = null;
        if (payload.getRating() instanceof Map) {
            try {
                rating = objectMapper.convertValue(payload.getRating(), RatingDto.class);
            } catch (Exception e) {
                log.warn("Could not convert rating: {}", e.getMessage());
            }
        }

        return SettlementResponse.builder()
                .bookingId(booking.getId())
                .bookingCode("BK-" + String.format("%03d", booking.getId()))
                .waybillNo(booking.getWaybillNo())
                .status(booking.getStatus())
                .totalBasePaid(totalBasePaid)
                .items(items)
                .totalExtraAmount(extraTotal)
                .issuedAt(issuedAt)
                .dueAt(dueAt)
                .issuedBy(issuedBy)
                .paid(paidInfo != null)
                .paidInfo(paidInfo)
                .rating(rating)
                .build();
    }

    private Booking findBookingByIdentifier(String bookingIdentifier) {
        if (bookingIdentifier == null || bookingIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }
        Integer id = parseBookingId(bookingIdentifier);
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
    }

    private Integer parseBookingId(String bookingIdentifier) {
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
        return BookingDetailsJson.read(objectMapper, booking);
    }

    private void savePayload(Booking booking, BookingDetailsPayload payload) {
        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("Could not serialize payload for booking {}: {}", booking.getId(), ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapFromObject(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return null;
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

    private BookingResponse mapToBookingResponse(Booking b) {
        BookingDetailsPayload payload = extractPayload(b);
        Object clearance = ClearanceDocumentPolicy.forBooking(
                payload.getClearance(), "international".equalsIgnoreCase(b.getBookingType()));
        String customerFullName = (b.getCustomerID() != null && b.getCustomerID().getUser() != null)
                ? b.getCustomerID().getUser().getUsername()
                : "Khách hàng";

        return BookingResponse.builder()
                .id("BK-" + String.format("%03d", b.getId()))
                .numericId(b.getId())
                .type(b.getBookingType() != null ? b.getBookingType() : "domestic")
                .customer(customerFullName)
                .customerName(customerFullName)
                .status(b.getStatus() != null ? b.getStatus() : "pending_intake")
                .createdAt(b.getBookingDate() != null ? b.getBookingDate().toEpochMilli() : System.currentTimeMillis())
                .departAt(b.getDepartureDate() != null ? b.getDepartureDate().toEpochMilli() : System.currentTimeMillis())
                .originName(b.getPickupAddress())
                .originCountry(payload != null ? payload.getOriginCountry() : null)
                .destName(b.getDropoffAddress())
                .destCountry(payload != null ? payload.getDestCountry() : null)
                .consignor(PartyContactDto.consignorOf(b))
                .consignee(PartyContactDto.consigneeOf(b))
                .totalHorses(b.getTotalHorses() != null ? b.getTotalHorses() : 1)
                .horses(payload.getHorses())
                .waybillNo(b.getWaybillNo())
                .trips(payload.getTrips())
                .route(payload.getRoute())
                .gate(payload.getGate())
                .plan(payload.getPlan())
                .quote(payload.getQuote())
                .payment(payload.getPayment())
                .medical(payload.getMedical())
                .clearance(clearance)
                .settlement(payload.getSettlement())
                .rating(payload.getRating())
                .intake(payload.getIntake())
                .rejection(payload.getRejection())
                .history(payload.getHistory())
                .build();
    }
}
