package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.settlement.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.SettlementService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.ClearanceDocumentPolicy;
import com.example.racehorse_transport.util.PaymentAmountLimits;
import com.example.racehorse_transport.util.IncidentCostPolicy;
import com.example.racehorse_transport.util.CustomerBookingPrivacy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final BookingRepository bookingRepository;
    private final IncidentRepository incidentRepository;
    private final StaffRepository staffRepository;
    private final ObjectMapper objectMapper;
    private final com.example.racehorse_transport.service.StaffNotifier staffNotifier;

    /** The incident ledger is authoritative; client copies never create extra expenses. */
    @Override
    @Transactional
    public BookingResponse submitExpenses(String bookingIdentifier, SubmitExpensesRequest request, String username) {
        Booking booking = lockedBooking(bookingIdentifier);
        if (!"delivered_pending_settlement".equals(booking.getStatus())) {
            throw new IllegalStateException("Bảng kê chi phí của đơn này đã gửi rồi.");
        }
        BookingDetailsPayload payload = extractPayload(booking);
        requireAssignedCrew(payload, username);
        if (request != null && request.getExpenses() != null) {
            request.getExpenses().forEach(item -> IncidentCostPolicy.requireCompany(item.getPayer()));
        }
        List<ExpenseItemDto> expenses = recordedExpenses(booking, payload);
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("internal", true);
        review.put("items", expenses);
        review.put("total", totalOf(expenses));
        review.put("submittedAt", System.currentTimeMillis());
        review.put("submittedBy", username);
        payload.setSettlement(review);
        booking.setStatus("expenses_submitted");
        appendHistory(payload, username, "Gửi bảng kê chi phí nội bộ công ty");
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        staffNotifier.toManagers(saved, "Đối soát chi phí nội bộ · BK-" + saved.getId(),
                "Có " + expenses.size() + " khoản chi phí công ty cần kiểm tra.", "/manager");
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public SettlementResponse issueSettlement(String bookingIdentifier, IssueSettlementRequest request, String username) {
        Booking booking = lockedBooking(bookingIdentifier);
        if (!Set.of("expenses_submitted", "settlement_issued", "payment_overdue").contains(String.valueOf(booking.getStatus()))) {
            throw new IllegalStateException("Đơn không ở bước đối soát chi phí nội bộ.");
        }
        if (request != null) {
            if (request.getPayers() != null) request.getPayers().values().forEach(IncidentCostPolicy::requireCompany);
            if (request.getItems() != null) request.getItems().forEach(item -> IncidentCostPolicy.requireCompany(item.getPayer()));
            if (request.getAdjustment() != null && request.getAdjustment().signum() != 0) {
                throw new IllegalArgumentException("Chi phí nội bộ phải khớp chứng từ thực chi.");
            }
        }
        BookingDetailsPayload payload = extractPayload(booking);
        List<ExpenseItemDto> items = recordedExpenses(booking, payload);
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("internal", true);
        review.put("items", items);
        review.put("total", totalOf(items));
        review.put("reviewedAt", System.currentTimeMillis());
        review.put("reviewedBy", username);
        review.put("note", request != null ? request.getNote() : "");
        payload.setSettlement(review);
        booking.setStatus("completed");
        appendHistory(payload, username, "Đã duyệt chi phí nội bộ và hoàn tất đơn");
        // Resource availability is changed exclusively by the HQ return workflow.
        savePayload(booking, payload);
        bookingRepository.save(booking);
        return buildSettlementResponse(booking, payload, review);
    }

    @Override
    @Transactional
    public BookingResponse submitRating(String bookingIdentifier, RatingDto rating, String username) {
        Booking booking = lockedBooking(bookingIdentifier);
        if (booking.getCustomerID() == null || booking.getCustomerID().getUser() == null
                || !Objects.equals(username, booking.getCustomerID().getUser().getUsername())) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền đánh giá đơn này.");
        }
        if (!"completed".equals(booking.getStatus())) throw new IllegalStateException("Chỉ đánh giá khi đơn đã hoàn tất.");
        if (rating == null || rating.getStars() == null || rating.getStars() < 1 || rating.getStars() > 5) {
            throw new IllegalArgumentException("Điểm đánh giá phải từ 1 đến 5.");
        }
        BookingDetailsPayload payload = extractPayload(booking);
        if (payload.getRating() != null) throw new IllegalStateException("Đơn này đã được đánh giá.");
        long now = System.currentTimeMillis();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("stars", rating.getStars());
        value.put("comment", rating.getComment());
        value.put("at", now);
        value.put("submittedAt", now);
        payload.setRating(value);
        savePayload(booking, payload);
        return CustomerBookingPrivacy.apply(mapToBookingResponse(bookingRepository.save(booking)));
    }

    /** Only pre-existing VNPAY callbacks use this method; no new settlement links exist. */
    @Override
    @Transactional
    public Booking confirmSettlementPaid(Payment payment, String actor) {
        Booking booking = payment.getBookingID();
        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> review = getMapFromObject(payload.getSettlement());
        review = review == null ? new LinkedHashMap<>() : new LinkedHashMap<>(review);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("paidAt", payment.getPaymentDate() != null ? payment.getPaymentDate().toEpochMilli() : System.currentTimeMillis());
        legacy.put("amount", payment.getAmount());
        legacy.put("reference", payment.getTransactionCode());
        review.put("legacyPaid", legacy);
        review.put("internal", true);
        payload.setSettlement(review);
        savePayload(booking, payload);
        return bookingRepository.save(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementResponse getSettlementDetail(String bookingIdentifier) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> review = getMapFromObject(payload.getSettlement());
        return buildSettlementResponse(booking, payload, review != null ? review : Collections.emptyMap());
    }

    @Override
    @Transactional
    public int migrateLegacySettlements() {
        int changed = 0;
        for (Incident incident : incidentRepository.findAll()) {
            boolean dirty = !"COMPANY".equals(incident.getCostBearer());
            incident.setCostBearer("COMPANY");
            if (incident.getExpensesJson() != null && !incident.getExpensesJson().isBlank()) {
                List<Map<String, Object>> entries = expenseMaps(incident);
                for (Map<String, Object> item : entries) {
                    if (!"carrier".equals(item.get("payer"))) { item.put("payer", "carrier"); dirty = true; }
                }
                BigDecimal total = entries.stream().map(item -> new BigDecimal(String.valueOf(item.get("amount"))))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (incident.getActualCost() == null || total.compareTo(incident.getActualCost()) != 0) dirty = true;
                incident.setActualCost(total);
                if (dirty) incident.setExpensesJson(writeJson(entries));
            } else if ("HANDOVER_TIMEOUT".equals(incident.getIncidentType())
                    && (incident.getInvoiceUrl() == null || incident.getInvoiceUrl().isBlank()) && incident.getActualCost() != null) {
                // Old timeout records copied estimatedCost into actualCost without a receipt.
                incident.setActualCost(null);
                dirty = true;
            }
            if (dirty) { incidentRepository.save(incident); changed++; }
        }
        for (String status : List.of("settlement_issued", "payment_overdue")) {
            for (Booking booking : bookingRepository.findByStatusOrderByBookingDateDesc(status)) {
                BookingDetailsPayload payload = extractPayload(booking);
                Map<String, Object> review = new LinkedHashMap<>();
                List<ExpenseItemDto> items = recordedExpenses(booking, payload);
                review.put("internal", true);
                review.put("items", items);
                review.put("total", totalOf(items));
                review.put("submittedAt", System.currentTimeMillis());
                Map<String, Object> previous = getMapFromObject(payload.getSettlement());
                if (previous != null && previous.get("paid") != null) review.put("legacyPaid", previous.get("paid"));
                payload.setSettlement(review);
                booking.setStatus("expenses_submitted");
                appendHistory(payload, "Hệ thống", "Chuyển chi phí phát sinh sang đối soát nội bộ công ty");
                savePayload(booking, payload);
                bookingRepository.save(booking);
                changed++;
            }
        }
        return changed;
    }

    private Booking lockedBooking(String identifier) {
        return bookingRepository.findByIdForUpdate(parseBookingId(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + identifier));
    }

    private void requireAssignedCrew(BookingDetailsPayload payload, String username) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream().noneMatch(a ->
                Set.of("ROLE_DRIVER", "ROLE_ESCORT").contains(a.getAuthority()))) return;
        var staff = staffRepository.findByUser_Username(username)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Không tìm thấy kíp xe."));
        Set<String> ids = new HashSet<>(List.of(username, String.valueOf(staff.getId())));
        if (staff.getStaffCode() != null) ids.add(staff.getStaffCode());
        boolean assigned = payload.getTrips() != null && payload.getTrips().stream()
                .anyMatch(t -> ids.contains(t.getDriverId()) || ids.contains(t.getEscortId()));
        if (!assigned) throw new org.springframework.security.access.AccessDeniedException("Bạn không được phân công cho đơn này.");
    }

    private List<ExpenseItemDto> recordedExpenses(Booking booking, BookingDetailsPayload payload) {
        List<ExpenseItemDto> items = new ArrayList<>();
        List<Incident> ledger = incidentRepository.findByBookingID_Id(booking.getId());
        for (Incident incident : ledger) {
            List<Map<String, Object>> entries = expenseMaps(incident);
            Set<String> seen = new HashSet<>();
            for (Map<String, Object> entry : entries) {
                String id = Objects.toString(entry.get("id"), "");
                if (id.isBlank() || !seen.add(id)) throw new IllegalStateException("Chứng từ sự cố thiếu mã hoặc trùng mã.");
                String photo = Objects.toString(entry.get("photo"), "");
                BigDecimal amount = new BigDecimal(String.valueOf(entry.get("amount")));
                validateExpense(amount, photo);
                items.add(ExpenseItemDto.builder().id("INC-" + incident.getId() + "/" + id)
                        .category(Objects.toString(entry.get("category"), "other"))
                        .description(Objects.toString(entry.get("label"), "Chi phí sự cố"))
                        .photo(photo).amount(amount).payer("carrier").build());
            }
            if (entries.isEmpty() && incident.getActualCost() != null && incident.getActualCost().signum() > 0
                    && incident.getInvoiceUrl() != null && !incident.getInvoiceUrl().isBlank()) {
                validateExpense(incident.getActualCost(), incident.getInvoiceUrl());
                items.add(ExpenseItemDto.builder().id("INC-" + incident.getId() + "/receipt")
                        .category("other").description(incident.getIncidentName()).photo(incident.getInvoiceUrl())
                        .amount(incident.getActualCost()).payer("carrier").build());
            }
        }
        if (ledger.isEmpty()) {
            Map<String, Object> legacy = getMapFromObject(payload.getSettlement());
            if (legacy != null && legacy.get("items") instanceof List<?> entries) {
                List<ExpenseItemDto> archived = objectMapper.convertValue(entries, new TypeReference<List<ExpenseItemDto>>() {});
                Set<String> seen = new HashSet<>();
                for (int index = 0; index < archived.size(); index++) {
                    ExpenseItemDto item = archived.get(index);
                    validateExpense(item.getAmount(), item.getPhoto());
                    String id = item.getId() != null ? item.getId() : "legacy-" + index;
                    if (!seen.add(id)) continue;
                    item.setId(id);
                    item.setPayer("carrier");
                    items.add(item);
                }
            }
        }
        // Submitted snapshots are never added to the live ledger a second time.
        return items;
    }

    private List<Map<String, Object>> expenseMaps(Incident incident) {
        if (incident.getExpensesJson() == null || incident.getExpensesJson().isBlank()) return new ArrayList<>();
        try { return objectMapper.readValue(incident.getExpensesJson(), new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Không đọc được chứng từ sự cố INC-" + incident.getId(), ex); }
    }

    private void validateExpense(BigDecimal amount, String photo) {
        if (amount == null || amount.signum() <= 0 || photo == null || photo.isBlank()) {
            throw new IllegalArgumentException("Khoản chi nội bộ cần số tiền dương và ảnh chứng từ.");
        }
        if (PaymentAmountLimits.exceedsMaximum(amount)) throw new IllegalArgumentException("Chi phí vượt quá giới hạn hỗ trợ.");
    }

    private BigDecimal totalOf(List<ExpenseItemDto> items) {
        return items.stream().map(ExpenseItemDto::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Không lưu được chi phí nội bộ.", ex); }
    }

    private BigDecimal toBigDecimal(Object value) {
        return value instanceof Number number ? new BigDecimal(number.toString()) : BigDecimal.ZERO;
    }

    private SettlementResponse buildSettlementResponse(Booking booking, BookingDetailsPayload payload, Map<String, Object> review) {
        List<ExpenseItemDto> items = review.get("items") instanceof List<?> entries
                ? objectMapper.convertValue(entries, new TypeReference<List<ExpenseItemDto>>() {}) : List.of();
        return SettlementResponse.builder()
                .bookingId(booking.getId())
                .bookingCode("BK-" + String.format("%03d", booking.getId()))
                .status(booking.getStatus())
                .items(items)
                .totalCompanyCost(toBigDecimal(review.get("total")))
                .reviewedAt(review.get("reviewedAt") instanceof Number time ? time.longValue() : null)
                .reviewedBy((String) review.get("reviewedBy"))
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
        if (bookingIdentifier == null || bookingIdentifier.isBlank()) throw new IllegalArgumentException("Thiếu mã đơn hàng.");
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
            throw new IllegalStateException("Không lưu được chi tiết đơn " + booking.getId(), ex);
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
                .trips(com.example.racehorse_transport.service.CaptureService.visibleTrips(payload))
                .route(payload.getRoute())
                .gate(payload.getGate())
                .plan(payload.getPlan())
                .photoEvidence(com.example.racehorse_transport.service.CaptureService.visibleEvidence(payload))
                .quote(payload.getQuote())
                .payment(payload.getPayment())
                .medical(payload.getMedical())
                .clearance(clearance)
                .settlement(payload.getSettlement())
                .rating(payload.getRating())
                .intake(payload.getIntake())
                .rejection(payload.getRejection())
                .sendBack(payload.getSendBack())
                .history(payload.getHistory())
                .build();
    }
}
