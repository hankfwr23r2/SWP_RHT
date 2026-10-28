package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.DocumentRequirementSnapshot;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.dto.specialist.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.SpecialistService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.ClearanceDocumentPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecialistServiceImpl implements SpecialistService {

    private final BookingRepository bookingRepository;
    private final StaffRepository staffRepository;
    private final TripDocumentRepository tripDocumentRepository;
    private final RegulationRepository regulationRepository;
    private final LocationRepository locationRepository;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // FLOW 1: DUYỆT HỒ SƠ NGỰA (VERIFICATION)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getVerificationBookings(String username) {
        List<Booking> all = bookingRepository.findAllByOrderByBookingDateDesc();
        Staff actor = findStaff(username);
        // Lấy các đơn ở giai đoạn thẩm định hồ sơ ngựa
        return all.stream()
                .filter(b -> {
                    String s = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
                    boolean inPhase = s.contains("review") || s.contains("verification") || s.contains("pending_intake")
                            || s.contains("intake") || s.contains("resubmission_requested");
                    Object medical = extractPayload(b).getMedical();
                    boolean approvedAwaitingQuotation = "pending_commercial".equals(s)
                            && medical instanceof Map<?, ?> medicalMap
                            && "approved".equalsIgnoreCase(Objects.toString(medicalMap.get("status"), ""));
                    return (inPhase || approvedAwaitingQuotation)
                            && (isPrivileged(actor) || isAssignedToSpecialist(actor, b));
                })
                .map(this::mapToBookingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingDetail(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        return mapToBookingResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse approveMedical(String bookingIdentifier, ApproveMedicalRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        if (!"under_review".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ duyệt hồ sơ ngựa khi đơn đang ở bước thẩm định.");
        }
        BookingDetailsPayload payload = extractPayload(booking);

        String specialist = (request != null && request.getSpecialistName() != null && !request.getSpecialistName().isBlank())
                ? request.getSpecialistName()
                : (username != null ? username : "Chuyên viên");

        Map<String, Object> medicalMap = getOrCreateMap(payload.getMedical());
        medicalMap.put("status", "approved");
        medicalMap.put("conclusion", "passed");
        medicalMap.put("reviewedAt", System.currentTimeMillis());
        medicalMap.put("reviewer", specialist);
        medicalMap.put("note", request != null && request.getNote() != null ? request.getNote() : "Hồ sơ đầy đủ, hợp lệ.");
        payload.setMedical(medicalMap);

        appendHistory(payload, specialist, "Duyệt hồ sơ ngựa ĐẠT");

        // Kiểm tra xem Coordinator đã chốt xe và lộ trình chưa
        boolean planConfirmed = payload.getTrips() != null && !payload.getTrips().isEmpty() && payload.getPlan() != null;
        if (planConfirmed) {
            booking.setStatus("pending_commercial");
            appendHistory(payload, "Hệ thống", "Hồ sơ ngựa và phương án xe đã duyệt xong");
        } else {
            booking.setStatus("under_review");
            appendHistory(payload, specialist, "Đã duyệt hồ sơ ngựa ĐẠT");
        }

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Specialist approved horse profiles for booking ID={}", saved.getId());
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse requestResubmission(String bookingIdentifier, RequestResubmissionRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        if (!"under_review".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ yêu cầu bổ sung hồ sơ ngựa khi đơn đang ở bước thẩm định.");
        }
        BookingDetailsPayload payload = extractPayload(booking);

        String specialist = (request != null && request.getSpecialistName() != null && !request.getSpecialistName().isBlank())
                ? request.getSpecialistName()
                : (username != null ? username : "Chuyên viên");

        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Hồ sơ chưa đạt yêu cầu kiểm dịch";

        Map<String, Object> resubmitInfo = new HashMap<>();
        resubmitInfo.put("reason", reason);
        resubmitInfo.put("items", request != null ? request.getItems() : Collections.emptyList());
        resubmitInfo.put("at", System.currentTimeMillis());

        Map<String, Object> medicalMap = getOrCreateMap(payload.getMedical());
        medicalMap.put("status", "resubmit");
        medicalMap.put("resubmit", resubmitInfo);
        medicalMap.put("conclusion", "rejected");
        medicalMap.put("reason", reason);
        medicalMap.put("items", request != null ? request.getItems() : Collections.emptyList());
        medicalMap.put("requestedAt", System.currentTimeMillis());
        medicalMap.put("requestedBy", specialist);
        payload.setMedical(medicalMap);

        booking.setStatus("under_review");

        appendHistory(payload, specialist, "Yêu cầu bổ sung hồ sơ ngựa");

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Specialist requested resubmission for booking ID={}: reason={}", saved.getId(), reason);
        return mapToBookingResponse(saved);
    }

    // =========================================================================
    // FLOW 2: THỦ TỤC KIỂM DỊCH & HẢI QUAN (CLEARANCE)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getClearanceBookings(String username) {
        List<Booking> all = bookingRepository.findAllByOrderByBookingDateDesc();
        Staff actor = findStaff(username);
        // Lấy các đơn ở giai đoạn làm giấy tờ kiểm dịch và hải quan
        return all.stream()
                .filter(b -> {
                    String s = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
                    boolean inPhase = s.equals("waybill_issued") || s.equals("clearance_in_progress")
                            || s.equals("clearance_done") || s.equals("ready_for_pickup")
                            || s.contains("clearance");
                    return inPhase && (isPrivileged(actor) || isAssignedToSpecialist(actor, b));
                })
                .map(this::mapToBookingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BookingResponse acceptWaybill(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayload payload = extractPayload(booking);

        booking.setStatus("clearance_in_progress");

        // Khởi tạo các hạng mục giấy tờ nếu chưa có
        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        clearanceMap.put("acceptedAt", System.currentTimeMillis());
        clearanceMap.put("specialistName", username != null ? username : "Chuyên viên");
        payload.setClearance(clearanceMap);

        appendHistory(payload, username != null ? username : "Chuyên viên",
                "Tiếp nhận Vận đơn");
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Specialist accepted waybill for booking ID={}", saved.getId());
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse updateClearanceItem(String bookingIdentifier, UpdateClearanceItemRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayload payload = extractPayload(booking);

        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        List<Map<String, Object>> items = getClearanceItemsList(clearanceMap);

        String type = request.getType();
        requireApplicableClearanceType(booking, type);
        Map<String, Object> targetItem = items.stream()
                .filter(i -> type.equalsIgnoreCase((String) i.get("type")))
                .findFirst()
                .orElse(null);

        if (targetItem == null) {
            targetItem = new HashMap<>();
            targetItem.put("type", type);
            items.add(targetItem);
        }

        if (request.getNote() != null) {
            targetItem.put("note", request.getNote());
        }

        List<String> photos = request.getPhotos() != null ? request.getPhotos() : Collections.emptyList();
        targetItem.put("photos", photos);

        // Quy tắc PRD 3.3: Tải ảnh lên -> tự chuyển 'done'; xóa hết ảnh -> về 'todo'
        String newStatus = !photos.isEmpty() ? "done" : "todo";
        targetItem.put("status", newStatus);
        targetItem.put("updatedAt", System.currentTimeMillis());
        targetItem.put("updatedBy", username != null ? username : "Chuyên viên");

        appendHistory(payload, username != null ? username : "Chuyên viên",
                "Cập nhật giấy tờ kiểm dịch");

        payload.setClearance(clearanceMap);
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Updated clearance item [{}] for booking ID={}", type, saved.getId());
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse addClearanceItem(String bookingIdentifier, AddClearanceItemRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayload payload = extractPayload(booking);

        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        List<Map<String, Object>> items = getClearanceItemsList(clearanceMap);

        String type = request.getType();
        requireApplicableClearanceType(booking, type);
        boolean exists = items.stream().anyMatch(i -> type.equalsIgnoreCase((String) i.get("type")));
        if (!exists) {
            Map<String, Object> newItem = new HashMap<>();
            newItem.put("type", type);
            newItem.put("label", request.getLabel() != null ? request.getLabel() : type);
            newItem.put("note", request.getNote() != null ? request.getNote() : "");
            newItem.put("status", "todo");
            newItem.put("photos", Collections.emptyList());
            items.add(newItem);

            appendHistory(payload, username != null ? username : "Chuyên viên", "Bổ sung thêm hạng mục giấy tờ: " + type);
        }

        payload.setClearance(clearanceMap);
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse markHorseCleared(String bookingIdentifier, MarkHorseClearedRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayload payload = extractPayload(booking);

        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        List<String> horsesCleared = getHorsesClearedList(clearanceMap);

        String horseId = request.getHorseId();
        if (request.isCleared()) {
            if (!horsesCleared.contains(horseId)) {
                horsesCleared.add(horseId);
            }
        } else {
            horsesCleared.remove(horseId);
        }
        clearanceMap.put("horsesCleared", horsesCleared);

        appendHistory(payload, username != null ? username : "Chuyên viên",
                (request.isCleared() ? "Ghi nhận thông quan ngựa" : "Hủy ghi nhận thông quan ngựa"));

        payload.setClearance(clearanceMap);
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse completeClearance(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayload payload = extractPayload(booking);

        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        List<Map<String, Object>> items = getClearanceItemsList(clearanceMap);

        // Kiểm tra xem tất cả hạng mục đã có trạng thái 'done' chưa
        boolean allDone = items.stream().allMatch(i -> "done".equalsIgnoreCase((String) i.get("status")));
        if (!allDone) {
            throw new IllegalStateException("Chưa thể hoàn tất: Vẫn còn hạng mục giấy tờ chưa nộp!");
        }

        // Tuyến quốc tế: kiểm tra xem tất cả ngựa đã có giấy thông quan chưa
        boolean isInternational = "international".equalsIgnoreCase(booking.getBookingType());
        if (isInternational) {
            List<String> horsesCleared = getHorsesClearedList(clearanceMap);
            int totalHorses = booking.getTotalHorses() != null ? booking.getTotalHorses() : 0;
            if (horsesCleared.size() < totalHorses) {
                throw new IllegalStateException("Chưa thể hoàn tất: Tuyến quốc tế yêu cầu tất cả ngựa (" + totalHorses + ") phải có giấy thông quan!");
            }
        }

        // Đơn chỉ sẵn sàng đón ngựa khi tài xế và hộ tống của mọi xe đã nhận lệnh
        List<com.example.racehorse_transport.dto.coordinator.VehicleTripDto> trips = payload.getTrips();
        boolean crewAcked = trips != null && !trips.isEmpty() && trips.stream().allMatch(t -> {
            Map<String, Object> acks = t.getAcks();
            return acks != null && acks.get("driver") != null && acks.get("escort") != null;
        });

        long now = System.currentTimeMillis();
        clearanceMap.put("doneAt", now);
        clearanceMap.put("doneBy", username != null ? username : "Chuyên viên");
        if (crewAcked) {
            clearanceMap.put("handedOverToCrewAt", now);
            clearanceMap.putIfAbsent("crewAckedAt", now);
        }
        clearanceMap.put("quarantineStatus", "APPROVED");
        clearanceMap.put("quarantineApprovedAt", now);
        clearanceMap.put("quarantineApprovedBy", username != null ? username : "Chuyên viên");
        payload.setClearance(clearanceMap);

        if (crewAcked) {
            booking.setStatus("ready_for_pickup");
            appendHistory(payload, username != null ? username : "Chuyên viên",
                    "Hoàn tất hồ sơ kiểm dịch & hải quan");
        } else {
            booking.setStatus("clearance_done");
            appendHistory(payload, username != null ? username : "Chuyên viên",
                    "Hoàn tất hồ sơ kiểm dịch & hải quan");
        }

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Clearance completed for booking ID={}, status={}", saved.getId(), saved.getStatus());
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse transferForQuarantine(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayload payload = extractPayload(booking);

        // Kiểm tra xem kíp xe (Driver & Escort) đã xác nhận chưa
        List<com.example.racehorse_transport.dto.coordinator.VehicleTripDto> trips = payload.getTrips();
        if (trips != null && !trips.isEmpty()) {
            boolean allAcked = trips.stream().allMatch(t -> {
                Map<String, Object> acks = t.getAcks();
                return acks != null && acks.get("driver") != null && acks.get("escort") != null;
            });
            if (!allAcked) {
                throw new IllegalStateException("Kíp xe (Tài xế và Hộ tống) chưa hoàn tất xác nhận giấy tờ chuyến đi!");
            }
        }

        // Lấy tên Specialist được phân công từ intake
        String specialistName = "Chuyên viên kiểm dịch";
        if (payload.getIntake() instanceof Map<?, ?> intakeMap && intakeMap.get("specialist") instanceof Map<?, ?> spMap) {
            if (spMap.get("name") != null) {
                specialistName = String.valueOf(spMap.get("name"));
            }
        }

        booking.setStatus("clearance_in_progress");

        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        clearanceMap.put("crewAckedAt", System.currentTimeMillis());
        clearanceMap.put("specialistName", specialistName);
        clearanceMap.put("quarantineStatus", "PENDING_SPECIALIST_REVIEW");
        payload.setClearance(clearanceMap);

        appendHistory(payload, username != null ? username : "Hệ thống",
                "Kíp xe đã xác nhận đủ giấy tờ. Chuyển đơn sang Chuyên viên kiểm dịch (" + specialistName + ") để duyệt xác nhận dịch tễ.");

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Booking ID={} transferred for quarantine review to Specialist [{}]", saved.getId(), specialistName);
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse approveQuarantine(String bookingIdentifier, ApproveMedicalRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayload payload = extractPayload(booking);

        String assignedSpecialist = null;
        if (payload.getIntake() instanceof Map<?, ?> intakeMap && intakeMap.get("specialist") instanceof Map<?, ?> spMap) {
            if (spMap.get("name") != null) {
                assignedSpecialist = String.valueOf(spMap.get("name"));
            }
        }

        String reviewer = (request != null && request.getSpecialistName() != null && !request.getSpecialistName().isBlank())
                ? request.getSpecialistName()
                : (username != null ? username : (assignedSpecialist != null ? assignedSpecialist : "Chuyên viên kiểm dịch"));

        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        clearanceMap.put("quarantineStatus", "APPROVED");
        clearanceMap.put("quarantineApprovedAt", System.currentTimeMillis());
        clearanceMap.put("quarantineApprovedBy", reviewer);
        String note = (request != null && request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote()
                : "Hồ sơ dịch tễ và kiểm dịch động vật của chuyến đi đạt chuẩn.";
        clearanceMap.put("quarantineNote", note);

        // Đánh dấu mục kiểm dịch health_cert trong clearance items
        List<Map<String, Object>> items = getClearanceItemsList(clearanceMap);
        for (Map<String, Object> item : items) {
            if ("health_cert".equalsIgnoreCase(String.valueOf(item.get("type")))) {
                item.put("status", "done");
                item.put("updatedAt", System.currentTimeMillis());
                item.put("updatedBy", reviewer);
                item.put("note", note);
            }
        }

        payload.setClearance(clearanceMap);

        appendHistory(payload, reviewer, "Chuyên viên kiểm dịch đã duyệt ĐẠT xác nhận dịch tễ cho chuyến đi: " + note);

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Specialist [{}] approved quarantine for booking ID={}", reviewer, saved.getId());
        return mapToBookingResponse(saved);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

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
        BookingDetailsPayload payload = BookingDetailsJson.read(objectMapper, booking);
        if (payload.getMedical() == null) {
            Map<String, Object> med = new HashMap<>();
            med.put("status", "pending");
            payload.setMedical(med);
        }
        return payload;
    }

    private Staff findStaff(String username) {
        if (username == null || username.isBlank()) return null;
        return staffRepository.findByUser_Username(username)
                .or(() -> staffRepository.findAll().stream()
                        .filter(staff -> staff.getUser() != null
                                && (username.equalsIgnoreCase(staff.getUser().getUsername())
                                || username.equalsIgnoreCase(staff.getUser().getEmail())))
                        .findFirst())
                .orElse(null);
    }

    private boolean isPrivileged(Staff actor) {
        return actor != null && ("MANAGER".equalsIgnoreCase(actor.getRole()) || "ADMIN".equalsIgnoreCase(actor.getRole()));
    }

    private boolean isAssignedToSpecialist(Staff actor, Booking booking) {
        if (actor == null) return false;
        Object intakeValue = extractPayload(booking).getIntake();
        if (!(intakeValue instanceof Map<?, ?> intake)) return false;
        Object specialistValue = intake.get("specialist");
        if (!(specialistValue instanceof Map<?, ?> specialist)) return false;

        Set<String> assignedIdentities = new HashSet<>();
        addIdentity(assignedIdentities, specialist.get("id"));
        addIdentity(assignedIdentities, specialist.get("name"));

        Set<String> staffIdentities = new HashSet<>();
        addIdentity(staffIdentities, actor.getStaffCode());
        addIdentity(staffIdentities, actor.getId());
        if (actor.getUser() != null) {
            addIdentity(staffIdentities, actor.getUser().getUsername());
            addIdentity(staffIdentities, actor.getUser().getEmail());
        }
        return assignedIdentities.stream().anyMatch(staffIdentities::contains);
    }

    private void addIdentity(Set<String> identities, Object identity) {
        if (identity != null && !identity.toString().isBlank()) {
            identities.add(identity.toString().trim().toLowerCase(Locale.ROOT));
        }
    }

    private void requireAssignment(String username, Booking booking) {
        Staff actor = findStaff(username);
        if (!isPrivileged(actor) && !isAssignedToSpecialist(actor, booking)) {
            throw new AccessDeniedException("Đơn hàng chưa được phân công cho tài khoản này.");
        }
    }

    private void savePayload(Booking booking, BookingDetailsPayload payload) {
        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("Could not serialize payload: {}", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateClearanceMap(BookingDetailsPayload payload, Booking booking) {
        if (payload.getClearance() instanceof Map) {
            Map<String, Object> existing = (Map<String, Object>) payload.getClearance();
            if (existing.get("items") instanceof List && !((List<?>) existing.get("items")).isEmpty()) {
                Object filtered = ClearanceDocumentPolicy.forBooking(
                        existing, "international".equalsIgnoreCase(booking.getBookingType()));
                if (filtered instanceof Map<?, ?> filteredMap) {
                    Map<String, Object> normalized = new HashMap<>();
                    filteredMap.forEach((key, value) -> {
                        if (key instanceof String stringKey) normalized.put(stringKey, value);
                    });
                    if (filtered != existing) payload.setClearance(normalized);
                    if (normalized.get("items") instanceof List<?> items && !items.isEmpty()) {
                        return normalized;
                    }
                }
            }
        }
        Map<String, Object> clearanceMap = (payload.getClearance() instanceof Map)
                ? (Map<String, Object>) payload.getClearance()
                : new HashMap<>();

        boolean isInternational = "international".equalsIgnoreCase(booking.getBookingType());
        String gateName = payload.getGate();

        Location borderGate = null;
        if (isInternational && gateName != null && !gateName.trim().isEmpty()) {
            String cleanGate = gateName.trim().toLowerCase();
            borderGate = locationRepository.findAll().stream()
                    .filter(l -> "BorderGate".equalsIgnoreCase(l.getLocationType()))
                    .filter(l -> {
                        String name = l.getLocationName() != null ? l.getLocationName().toLowerCase() : "";
                        return cleanGate.contains(name) || name.contains(cleanGate)
                                || (cleanGate.contains("mộc bài") && name.contains("mộc bài"))
                                || (cleanGate.contains("lao bảo") && name.contains("lao bảo"))
                                || (cleanGate.contains("tịnh biên") && name.contains("tịnh biên"))
                                || (cleanGate.contains("cầu treo") && name.contains("cầu treo"))
                                || (cleanGate.contains("hà tiên") && name.contains("hà tiên"))
                                || (cleanGate.contains("tây trang") && name.contains("tây trang"));
                    })
                    .findFirst()
                    .orElse(null);
        }

        List<Regulation> applicableRegs = new ArrayList<>();
        applicableRegs.addAll(regulationRepository.findByLocationIDIsNullAndStatus("ACTIVE"));
        if (isInternational) {
            if (borderGate != null) {
                applicableRegs.addAll(regulationRepository.findByLocationID_IdAndStatus(borderGate.getId(), "ACTIVE"));
            } else {
                applicableRegs.addAll(regulationRepository.findByStatus("ACTIVE").stream()
                        .filter(r -> r.getLocationID() != null)
                        .toList());
            }
        }
        boolean hasRequirementSnapshot = payload.getDocumentRequirements() != null;
        List<DocumentRequirementSnapshot> requiredClearanceDocuments = hasRequirementSnapshot
                ? payload.getDocumentRequirements().stream()
                        .filter(requirement -> "CLEARANCE".equals(requirement.getDocumentGroup()))
                        .toList()
                : List.of();
        String configuredDestinationCountry = payload.getDestCountry() != null ? payload.getDestCountry()
                : borderGate != null ? borderGate.getDestinationCountry() : null;
        applicableRegs = applicableRegs.stream()
                .filter(regulation -> ClearanceDocumentPolicy.isApplicable(regulation, isInternational))
                .filter(regulation -> !hasRequirementSnapshot
                        || ClearanceDocumentPolicy.isForDestinationCountry(regulation, configuredDestinationCountry))
                .filter(regulation -> !hasRequirementSnapshot || requiredClearanceDocuments.stream()
                        .anyMatch(requirement -> requirement.getDocumentType().equals(regulation.getDocumentType())))
                .toList();
        Map<String, DocumentRequirementSnapshot> requirementByType = requiredClearanceDocuments.stream()
                .collect(Collectors.toMap(
                        DocumentRequirementSnapshot::getDocumentType, requirement -> requirement, (first, ignored) -> first));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Regulation reg : applicableRegs) {
            Map<String, Object> item = new HashMap<>();
            String type = reg.getDocumentType() != null ? reg.getDocumentType() : ("doc_" + reg.getId());
            DocumentRequirementSnapshot requirement = requirementByType.get(type);
            item.put("type", type);
            item.put("label", requirement != null ? requirement.getDocumentName() : reg.getRegulationName());
            item.put("hint", requirement != null && requirement.getDescription() != null
                    ? requirement.getDescription() : reg.getDescription());
            item.put("note", "");
            item.put("status", "todo");
            item.put("photos", new ArrayList<String>());
            item.put("regulationId", reg.getId());
            if (reg.getLocationID() != null) {
                item.put("gateName", reg.getLocationID().getLocationName());
                item.put("gateLocationId", reg.getLocationID().getId());
            }
            items.add(item);
        }

        Set<String> presentTypes = items.stream()
                .map(item -> Objects.toString(item.get("type"), ""))
                .collect(Collectors.toSet());
        if (hasRequirementSnapshot) {
            for (DocumentRequirementSnapshot requirement : requiredClearanceDocuments) {
                if (presentTypes.add(requirement.getDocumentType())) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("type", requirement.getDocumentType());
                    item.put("label", requirement.getDocumentName());
                    item.put("hint", requirement.getDescription());
                    item.put("note", "");
                    item.put("status", "todo");
                    item.put("photos", new ArrayList<String>());
                    items.add(item);
                }
            }
        }

        if (items.isEmpty() && !hasRequirementSnapshot) {
            addLegacyFallbackItems(items, isInternational);
        }

        clearanceMap.put("items", items);
        if (!clearanceMap.containsKey("horsesCleared")) {
            clearanceMap.put("horsesCleared", new ArrayList<String>());
        }
        if (borderGate != null) {
            clearanceMap.put("gateName", borderGate.getLocationName());
            clearanceMap.put("gateLocationId", borderGate.getId());
        } else if (gateName != null) {
            clearanceMap.put("gateName", gateName);
        }

        payload.setClearance(clearanceMap);
        return clearanceMap;
    }

    private void addLegacyFallbackItems(List<Map<String, Object>> items, boolean isInternational) {
        addLegacyFallbackItem(items, "health_cert", "Giấy chứng nhận kiểm dịch động vật");
        addLegacyFallbackItem(items, "poa", "Giấy ủy quyền áp tải (PoA)");
        if (isInternational) {
            addLegacyFallbackItem(items, "customs_declaration", "Tờ khai hải quan điện tử");
            addLegacyFallbackItem(items, "import_permit", "Giấy phép nhập khẩu (Import Permit)");
        }
    }

    private void addLegacyFallbackItem(List<Map<String, Object>> items, String type, String label) {
        Map<String, Object> item = new HashMap<>();
        item.put("type", type);
        item.put("label", label);
        item.put("status", "todo");
        item.put("photos", new ArrayList<String>());
        items.add(item);
    }

    private void requireApplicableClearanceType(Booking booking, String type) {
        if (!"international".equalsIgnoreCase(booking.getBookingType())
                && ClearanceDocumentPolicy.isInternationalOnlyType(type)) {
            throw new IllegalArgumentException("Loại giấy tờ quốc tế này không áp dụng cho đơn trong nước.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getClearanceItemsList(Map<String, Object> clearanceMap) {
        Object itemsObj = clearanceMap.get("items");
        if (itemsObj instanceof List) {
            return (List<Map<String, Object>>) itemsObj;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        clearanceMap.put("items", items);
        return items;
    }

    @SuppressWarnings("unchecked")
    private List<String> getHorsesClearedList(Map<String, Object> clearanceMap) {
        Object obj = clearanceMap.get("horsesCleared");
        if (obj instanceof List) {
            return (List<String>) obj;
        }
        List<String> list = new ArrayList<>();
        clearanceMap.put("horsesCleared", list);
        return list;
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
                .intake(payload.getIntake())
                .rejection(payload.getRejection())
                .history(payload.getHistory())
                .build();
    }
}
