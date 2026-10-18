package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.specialist.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.SpecialistService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecialistServiceImpl implements SpecialistService {

    private final BookingRepository bookingRepository;
    private final TripDocumentRepository tripDocumentRepository;
    private final RegulationRepository regulationRepository;
    private final LocationRepository locationRepository;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // FLOW 1: DUYỆT HỒ SƠ NGỰA (VERIFICATION)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getVerificationBookings() {
        List<Booking> all = bookingRepository.findAllByOrderByBookingDateDesc();
        // Lấy các đơn ở giai đoạn thẩm định hồ sơ ngựa
        return all.stream()
                .filter(b -> {
                    String s = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
                    return s.contains("review") || s.contains("verification") || s.contains("pending_intake")
                            || s.contains("intake") || s.contains("resubmission_requested");
                })
                .map(this::mapToBookingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingDetail(String bookingIdentifier) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        return mapToBookingResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse approveMedical(String bookingIdentifier, ApproveMedicalRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
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

        appendHistory(payload, specialist, "Duyệt hồ sơ ngựa ĐẠT: Đầy đủ Hộ chiếu, Sổ tiêm phòng và Xét nghiệm máu hợp lệ.");

        // Kiểm tra xem Coordinator đã chốt xe và lộ trình chưa
        boolean planConfirmed = payload.getTrips() != null && !payload.getTrips().isEmpty() && payload.getPlan() != null;
        if (planConfirmed) {
            booking.setStatus("pending_commercial");
            appendHistory(payload, "Hệ thống", "Hồ sơ ngựa và phương án xe đều đã duyệt xong -> Chuyển Quản lý duyệt báo giá.");
        } else {
            booking.setStatus("under_review");
            appendHistory(payload, specialist, "Đã duyệt hồ sơ ngựa ĐẠT. Đang chờ Điều phối viên chốt phương án xe và lộ trình.");
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

        appendHistory(payload, specialist, "Yêu cầu khách hàng bổ sung lại hồ sơ ngựa. Lý do: " + reason);

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
    public List<BookingResponse> getClearanceBookings() {
        List<Booking> all = bookingRepository.findAllByOrderByBookingDateDesc();
        // Lấy các đơn ở giai đoạn làm giấy tờ kiểm dịch và hải quan
        return all.stream()
                .filter(b -> {
                    String s = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
                    return s.equals("waybill_issued") || s.equals("clearance_in_progress")
                            || s.equals("clearance_done") || s.equals("ready_for_pickup")
                            || s.contains("clearance");
                })
                .map(this::mapToBookingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BookingResponse acceptWaybill(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        BookingDetailsPayload payload = extractPayload(booking);

        booking.setStatus("clearance_in_progress");

        // Khởi tạo các hạng mục giấy tờ nếu chưa có
        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        clearanceMap.put("acceptedAt", System.currentTimeMillis());
        clearanceMap.put("specialistName", username != null ? username : "Chuyên viên");
        payload.setClearance(clearanceMap);

        appendHistory(payload, username != null ? username : "Chuyên viên",
                "Tiếp nhận Vận đơn " + (booking.getWaybillNo() != null ? booking.getWaybillNo() : "") + ". Bắt đầu tiến hành thủ tục kiểm dịch & hải quan.");

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Specialist accepted waybill for booking ID={}", saved.getId());
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse updateClearanceItem(String bookingIdentifier, UpdateClearanceItemRequest request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        BookingDetailsPayload payload = extractPayload(booking);

        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        List<Map<String, Object>> items = getClearanceItemsList(clearanceMap);

        String type = request.getType();
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

        // Đồng bộ vào thực thể TRIP_DOCUMENT trong cơ sở dữ liệu nếu có ảnh
        syncTripDocument(booking, type, photos, newStatus, request.getNote());

        appendHistory(payload, username != null ? username : "Chuyên viên",
                "Cập nhật giấy tờ [" + type + "]: " + ("done".equals(newStatus) ? "ĐÃ NỘP (kèm ảnh)" : "CHƯA NỘP"));

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
        BookingDetailsPayload payload = extractPayload(booking);

        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        List<Map<String, Object>> items = getClearanceItemsList(clearanceMap);

        String type = request.getType();
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
                "Ngựa ID [" + horseId + "]: " + (request.isCleared() ? "Đã có giấy thông quan" : "Hủy ghi nhận thông quan"));

        payload.setClearance(clearanceMap);
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse completeClearance(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
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

        long now = System.currentTimeMillis();
        clearanceMap.put("doneAt", now);
        clearanceMap.put("doneBy", username != null ? username : "Chuyên viên");
        clearanceMap.put("handedOverToCrewAt", now);
        clearanceMap.put("crewAckedAt", now);
        clearanceMap.put("quarantineStatus", "APPROVED");
        clearanceMap.put("quarantineApprovedAt", now);
        clearanceMap.put("quarantineApprovedBy", username != null ? username : "Chuyên viên");
        payload.setClearance(clearanceMap);

        // Chuyển thẳng sang Sẵn sàng đón ngựa (Phương án 1: Tuyến tính hóa luồng)
        booking.setStatus("ready_for_pickup");
        appendHistory(payload, username != null ? username : "Chuyên viên",
                "Hoàn tất toàn bộ hồ sơ kiểm dịch & hải quan. Đã bàn giao hồ sơ cho kíp xe, đơn hàng sẵn sàng đón ngựa!");

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Clearance completed for booking ID={}, status={}", saved.getId(), saved.getStatus());
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse transferForQuarantine(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
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
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + bookingIdentifier));
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
        BookingDetailsPayload payload = new BookingDetailsPayload();
        if (booking.getDetailsJson() != null && !booking.getDetailsJson().trim().isEmpty()) {
            String json = booking.getDetailsJson().trim();
            try {
                if (json.startsWith("{")) {
                    payload = objectMapper.readValue(json, BookingDetailsPayload.class);
                }
            } catch (Exception ex) {
                log.warn("Error reading detailsJson: {}", ex.getMessage());
            }
        }
        if (payload.getMedical() == null) {
            Map<String, Object> med = new HashMap<>();
            med.put("status", "pending");
            payload.setMedical(med);
        }
        return payload;
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
                return existing;
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

        List<Map<String, Object>> items = new ArrayList<>();
        for (Regulation reg : applicableRegs) {
            Map<String, Object> item = new HashMap<>();
            String type = reg.getDocumentType() != null ? reg.getDocumentType() : ("doc_" + reg.getId());
            item.put("type", type);
            item.put("label", reg.getRegulationName());
            item.put("hint", reg.getDescription());
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

        // Fallback tối thiểu nếu bảng Regulation chưa có data
        if (items.isEmpty()) {
            Map<String, Object> healthCert = new HashMap<>();
            healthCert.put("type", "health_cert");
            healthCert.put("label", "Giấy chứng nhận kiểm dịch động vật");
            healthCert.put("status", "todo");
            healthCert.put("photos", new ArrayList<String>());
            items.add(healthCert);

            if (isInternational) {
                Map<String, Object> customs = new HashMap<>();
                customs.put("type", "customs_declaration");
                customs.put("label", "Tờ khai hải quan điện tử");
                customs.put("status", "todo");
                customs.put("photos", new ArrayList<String>());
                items.add(customs);

                Map<String, Object> importPermit = new HashMap<>();
                importPermit.put("type", "import_permit");
                importPermit.put("label", "Giấy phép nhập khẩu (Import Permit)");
                importPermit.put("status", "todo");
                importPermit.put("photos", new ArrayList<String>());
                items.add(importPermit);

                Map<String, Object> poa = new HashMap<>();
                poa.put("type", "poa");
                poa.put("label", "Giấy ủy quyền áp tải (PoA)");
                poa.put("status", "todo");
                poa.put("photos", new ArrayList<String>());
                items.add(poa);
            }
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

    private void syncTripDocument(Booking booking, String type, List<String> photos, String status, String note) {
        try {
            // Upsert: tìm bản ghi cũ theo booking + type, cập nhật nếu có, tạo mới nếu chưa có
            TripDocument doc = tripDocumentRepository
                    .findByBookingIdAndDocumentType(booking.getId(), type)
                    .orElse(TripDocument.builder()
                            .bookingID(booking)
                            .documentType(type)
                            .documentNumber("DOC-" + booking.getId() + "-" + type.toUpperCase())
                            .build());

            // Gán RegulationID nếu chưa có
            if (doc.getRegulationID() == null) {
                Regulation reg = regulationRepository.findAll().stream()
                        .filter(r -> type.equalsIgnoreCase(r.getDocumentType()))
                        .findFirst()
                        .orElse(null);
                if (reg != null) {
                    doc.setRegulationID(reg);
                }
            }

            doc.setFileUrl(!photos.isEmpty() ? photos.get(0) : null);
            doc.setIssueDate(LocalDate.now());
            doc.setStatus("done".equalsIgnoreCase(status) ? "VALID" : "PENDING");
            tripDocumentRepository.save(doc);
        } catch (Exception ex) {
            log.warn("Could not sync TripDocument: {}", ex.getMessage());
        }
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
                .destName(b.getDropoffAddress())
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
                .clearance(payload.getClearance())
                .intake(payload.getIntake())
                .rejection(payload.getRejection())
                .history(payload.getHistory())
                .build();
    }
}
