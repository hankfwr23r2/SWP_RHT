package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.HorseInsuranceDto;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.specialist.AddClearanceItemRequestDto;
import com.example.racehorse_transport.dto.specialist.ApproveMedicalRequestDto;
import com.example.racehorse_transport.dto.specialist.MarkHorseClearedRequestDto;
import com.example.racehorse_transport.dto.specialist.RequestResubmissionRequestDto;
import com.example.racehorse_transport.dto.specialist.UpdateClearanceItemRequestDto;
import com.example.racehorse_transport.dto.specialist.UpdateClearanceItemsRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripDocumentRepository;
import com.example.racehorse_transport.service.SpecialistService;
import com.example.racehorse_transport.service.StaffNotificationService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.BookingHistory;
import com.example.racehorse_transport.util.BookingSchedulePolicy;
import com.example.racehorse_transport.service.BookingVisibilityService;
import com.example.racehorse_transport.util.ClearanceDocumentPolicy;
import com.example.racehorse_transport.util.HorseInsurancePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class SpecialistServiceImpl implements SpecialistService {

    private final BookingRepository bookingRepository;
    private final StaffRepository staffRepository;
    private final TripDocumentRepository tripDocumentRepository;
    private final DocumentRequirementRepository documentRequirementRepository;
    private final LocationRepository locationRepository;
    private final ObjectMapper objectMapper;
    private final StaffNotificationService staffNotifier;
    private final BookingVisibilityService bookingVisibility;

    @Override
    @Transactional
    public BookingResponseDto updateInsurance(String id, String horseId, HorseInsuranceDto request, String username) {
        Booking booking = bookingRepository.findByIdForUpdate(parseBookingId(id)).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        requireAssignment(username, booking);
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
        var horse = payload.getHorses().stream().filter(h -> horseId.equals(h.getHorseId())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ngựa trong đơn."));
        var existing = horse.getInsurance();

        String status = Objects.toString(booking.getStatus(), "");
        boolean beforeQuote = Set.of("under_review", "pending_commercial", "resubmission_requested").contains(status);
        boolean afterDeposit = Set.of("waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup", "en_route_to_pickup").contains(status);
        if (!beforeQuote && !afterDeposit) throw new IllegalStateException("Không thể sửa bảo hiểm ở trạng thái hiện tại.");
        if ((existing == null || existing.getOption() == null) && !beforeQuote) throw new IllegalStateException("Không thay đổi bảo hiểm của báo giá cũ.");
        if (existing != null && existing.getOption() != null) request.setOption(existing.getOption());
        if (!beforeQuote) {
            // Phí, đơn vị và chứng từ báo giá đã chốt; chỉ bổ sung thông tin hợp đồng.
            request.setProvider(existing.getProvider()); request.setPremium(existing.getPremium());
            request.setReviewed(existing.getReviewed());
            if ("assistance".equals(existing.getOption())) {
                request.setDocuments(existing.getDocuments());
                request.setQuoteValidUntil(existing.getQuoteValidUntil());
                request.setNote(existing.getNote());
            }
        }
        if (beforeQuote && "assistance".equals(request.getOption())) {
            request.setPolicyNumber(null); request.setValidFrom(null); request.setValidUntil(null);
            request.setPolicyDocuments(null);
        }
        if (beforeQuote) request.setArranged(false);
        // The booking row is locked above: concurrent retries see the latest saved declaration.
        boolean alreadyConfirmed = existing != null && (beforeQuote
                ? Boolean.TRUE.equals(existing.getReviewed()) : Boolean.TRUE.equals(existing.getArranged()));
        if (alreadyConfirmed && HorseInsurancePolicy.sameDeclaration(existing, request)) return mapToBookingResponse(booking);
        if (afterDeposit && existing != null && Boolean.TRUE.equals(existing.getArranged())) {
            throw new IllegalStateException("Hợp đồng bảo hiểm đã được xác nhận có hiệu lực, không thể chỉnh sửa tại bước này.");
        }
        if (beforeQuote && existing != null && Boolean.TRUE.equals(existing.getReviewed())
                && !Boolean.TRUE.equals(request.getReviewed()) && HorseInsurancePolicy.sameContent(existing, request)) {
            throw new IllegalStateException("Báo giá bảo hiểm đã được xác nhận. Chỉ lưu nháp khi có thay đổi thông tin báo giá.");
        }
        if (beforeQuote && !Boolean.TRUE.equals(request.getReviewed())) HorseInsurancePolicy.validateDraft(request);
        else HorseInsurancePolicy.validateDeclaration(request);
        if (Boolean.TRUE.equals(request.getReviewed()) && "assistance".equals(request.getOption())
                && (HorseInsurancePolicy.blank(request.getProvider()) || request.getPremium() == null || request.getPremium().signum() <= 0))
            throw new IllegalArgumentException("Nhập đơn vị cung cấp và phí bảo hiểm lớn hơn 0.");
        if (beforeQuote && Boolean.TRUE.equals(request.getReviewed()) && "assistance".equals(request.getOption())
                && !HorseInsurancePolicy.blank(request.getQuoteValidUntil())
                && java.time.LocalDate.parse(request.getQuoteValidUntil()).isBefore(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw new IllegalArgumentException("Báo giá bảo hiểm đã hết hạn.");
        if ("personal".equals(request.getOption()) && Boolean.TRUE.equals(request.getReviewed()) && booking.getDepartureDate() != null) {
            var date = booking.getDepartureDate().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
            if (date.isBefore(java.time.LocalDate.parse(request.getValidFrom())) || date.isAfter(java.time.LocalDate.parse(request.getValidUntil())))
                throw new IllegalArgumentException("Bảo hiểm cá nhân không bao phủ ngày khởi hành dự kiến.");
        }
        if (Boolean.TRUE.equals(request.getArranged())) {
            if (!afterDeposit || HorseInsurancePolicy.blank(request.getPolicyNumber()))
                throw new IllegalArgumentException("Chỉ xác nhận hợp đồng sau đặt cọc, cần số hợp đồng.");
            HorseInsurancePolicy.validateDates(request);
            if (booking.getDepartureDate() != null) {
                var day = booking.getDepartureDate().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
                if (day.isBefore(java.time.LocalDate.parse(request.getValidFrom())) || day.isAfter(java.time.LocalDate.parse(request.getValidUntil())))
                    throw new IllegalArgumentException("Hợp đồng phải bao phủ ngày khởi hành.");
            }
        }
        BookingHistory.appendHistory(payload, username, "Cập nhật hồ sơ bảo hiểm cho " + horse.getName());
        horse.setInsurance(request);
        savePayload(booking, payload);
        bookingRepository.save(booking);
        return mapToBookingResponse(booking);
    }

    // =========================================================================
    // FLOW 1: DUYỆT HỒ SƠ NGỰA (VERIFICATION)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> getInsuranceBookings(String username) {
        Staff actor = findStaff(username);
        return bookingRepository.findAllByOrderByBookingDateDesc().stream()
            .filter(b -> isPrivileged(actor) || isAssignedToSpecialist(actor, b))
            .map(this::mapToBookingResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> getVerificationBookings(String username) {
        List<Booking> all = bookingRepository.findAllByOrderByBookingDateDesc();
        Staff actor = findStaff(username);
        // Lấy các đơn ở giai đoạn thẩm định hồ sơ ngựa
        return all.stream()
                .filter(b -> {
                    String s = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
                    boolean inPhase = s.contains("review") || s.contains("verification") || s.contains("pending_intake")
                            || s.contains("intake") || s.contains("resubmission_requested");
                    Object medical = BookingDetailsJson.readWithMedicalDefaults(objectMapper, b).getMedical();
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
    public BookingResponseDto getBookingDetail(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        return mapToBookingResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponseDto approveMedical(String bookingIdentifier, ApproveMedicalRequestDto request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier, true);
        requireAssignment(username, booking);
        BookingSchedulePolicy.requireCurrentSchedule(booking, java.time.Instant.now());
        if (!"under_review".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ duyệt hồ sơ ngựa khi đơn đang ở bước thẩm định.");
        }
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

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

        BookingHistory.appendHistory(payload, specialist, "Duyệt hồ sơ ngựa ĐẠT");
        if (payload.getSendBack() != null) payload.getSendBack().remove("specialist"); // đã duyệt lại theo lý do Quản lý trả

        // Kiểm tra xem Coordinator đã chốt xe và lộ trình chưa
        boolean planConfirmed = payload.getTrips() != null && !payload.getTrips().isEmpty() && payload.getPlan() != null;
        if (planConfirmed) {
            booking.setStatus("pending_commercial");
            BookingHistory.appendHistory(payload, "Hệ thống", "Hồ sơ ngựa và phương án xe đã duyệt xong");
        } else {
            booking.setStatus("under_review");
            BookingHistory.appendHistory(payload, specialist, "Đã duyệt hồ sơ ngựa ĐẠT");
        }

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        if (planConfirmed) {
            staffNotifier.toManagers(saved, "Đơn chờ duyệt báo giá · " + saved.getCode(),
                    "Chuyên viên đã duyệt hồ sơ và Điều phối viên đã chốt xe, lộ trình. Chọn kíp xe và gửi báo giá.", "/manager/approvals");
        }
        log.info("Specialist approved horse profiles for booking ID={}", saved.getId());
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponseDto requestResubmission(String bookingIdentifier, RequestResubmissionRequestDto request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        if (!"under_review".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Chỉ yêu cầu bổ sung hồ sơ ngựa khi đơn đang ở bước thẩm định.");
        }
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

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

        BookingHistory.appendHistory(payload, specialist, "Yêu cầu bổ sung hồ sơ ngựa");

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
    public List<BookingResponseDto> getClearanceBookings(String username) {
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
    public BookingResponseDto acceptWaybill(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

        booking.setStatus("clearance_in_progress");

        // Khởi tạo các hạng mục giấy tờ nếu chưa có
        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        clearanceMap.put("acceptedAt", System.currentTimeMillis());
        clearanceMap.put("specialistName", username != null ? username : "Chuyên viên");
        payload.setClearance(clearanceMap);

        BookingHistory.appendHistory(payload, username != null ? username : "Chuyên viên",
                "Tiếp nhận Vận đơn");
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Specialist accepted waybill for booking ID={}", saved.getId());
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponseDto updateClearanceItem(String bookingIdentifier, UpdateClearanceItemRequestDto request, String username) {
        return updateClearanceItems(bookingIdentifier,
                UpdateClearanceItemsRequestDto.builder().items(List.of(request)).build(), username);
    }

    @Override
    @Transactional
    public BookingResponseDto updateClearanceItems(String bookingIdentifier, UpdateClearanceItemsRequestDto request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        if (!Set.of("waybill_issued", "clearance_in_progress").contains(booking.getStatus())) {
            throw new IllegalStateException("Giấy tờ chỉ được sửa trong giai đoạn chuẩn bị.");
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Danh sách cập nhật giấy tờ không được để trống.");
        }
        Set<String> types = new HashSet<>();
        for (UpdateClearanceItemRequestDto patch : request.getItems()) {
            if (patch == null || patch.getType() == null || patch.getType().isBlank()
                    || !types.add(patch.getType().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Loại giấy tờ không hợp lệ hoặc bị lặp.");
            }
            requireApplicableClearanceType(booking, patch.getType());
        }
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
        Map<String, Object> clearanceMap = getOrCreateClearanceMap(payload, booking);
        List<Map<String, Object>> items = getClearanceItemsList(clearanceMap);
        boolean changed = false;
        for (UpdateClearanceItemRequestDto patch : request.getItems()) {
            String type = patch.getType().trim().toLowerCase(Locale.ROOT);
            Map<String, Object> item = items.stream().filter(i -> type.equalsIgnoreCase((String) i.get("type")))
                    .findFirst().orElse(null);
            boolean itemChanged = item == null;
            if (item == null) {
                item = new HashMap<>(); item.put("type", type); item.put("note", "");
                item.put("photos", List.of()); item.put("status", "todo"); items.add(item);
            }
            if (patch.getNote() != null && !Objects.equals(patch.getNote(), item.get("note"))) {
                item.put("note", patch.getNote()); itemChanged = true;
            }
            // Omitted photos means keep existing attachments; an explicit [] removes them.
            if (patch.getPhotos() != null && !Objects.equals(patch.getPhotos(), item.get("photos"))) {
                item.put("photos", new ArrayList<>(patch.getPhotos())); itemChanged = true;
            }
            if (itemChanged) {
                List<?> photos = item.get("photos") instanceof List<?> list ? list : List.of();
                item.put("status", photos.isEmpty() ? "todo" : "done");
                item.put("updatedAt", System.currentTimeMillis());
                item.put("updatedBy", username);
                changed = true;
            }
        }
        if (!changed) return mapToBookingResponse(booking);
        BookingHistory.appendHistory(payload, username != null ? username : "Chuyên viên", "Cập nhật giấy tờ kiểm dịch");
        payload.setClearance(clearanceMap);
        savePayload(booking, payload);
        return mapToBookingResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponseDto addClearanceItem(String bookingIdentifier, AddClearanceItemRequestDto request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

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

            BookingHistory.appendHistory(payload, username != null ? username : "Chuyên viên", "Bổ sung hạng mục giấy tờ");
        }

        payload.setClearance(clearanceMap);
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponseDto markHorseCleared(String bookingIdentifier, MarkHorseClearedRequestDto request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

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

        BookingHistory.appendHistory(payload, username != null ? username : "Chuyên viên",
                (request.isCleared() ? "Ghi nhận thông quan ngựa" : "Hủy ghi nhận thông quan ngựa"));

        payload.setClearance(clearanceMap);
        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponseDto completeClearance(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

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
        List<VehicleTripDto> trips = payload.getTrips();
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
            BookingHistory.appendHistory(payload, username != null ? username : "Chuyên viên",
                    "Hoàn tất hồ sơ kiểm dịch & hải quan");
        } else {
            booking.setStatus("clearance_done");
            BookingHistory.appendHistory(payload, username != null ? username : "Chuyên viên",
                    "Hoàn tất hồ sơ kiểm dịch & hải quan");
        }

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        staffNotifier.toCrew(saved, payload, "clearance_done".equals(saved.getStatus())
                        ? "Giấy tờ chuyến đã xong, cần bạn xác nhận · " + saved.getCode()
                        : "Sẵn sàng đón ngựa · " + saved.getCode(),
                "clearance_done".equals(saved.getStatus())
                        ? "Chuyên viên đã hoàn tất giấy tờ. Mở ứng dụng để nhận lệnh và xác nhận giấy tờ chuyến đi."
                        : "Giấy tờ đã xong và kíp xe đã nhận lệnh. Chuẩn bị xuất phát đến điểm đón đúng giờ.");
        log.info("Clearance completed for booking ID={}, status={}", saved.getId(), saved.getStatus());
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponseDto transferForQuarantine(String bookingIdentifier, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

        // Kiểm tra xem kíp xe (Driver & Escort) đã xác nhận chưa
        List<VehicleTripDto> trips = payload.getTrips();
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

        BookingHistory.appendHistory(payload, username != null ? username : "Hệ thống",
                "Kíp xe đã xác nhận đủ giấy tờ. Chuyển đơn sang Chuyên viên kiểm dịch (" + specialistName + ") để duyệt xác nhận dịch tễ.");

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Booking ID={} transferred for quarantine review to Specialist [{}]", saved.getId(), specialistName);
        return mapToBookingResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponseDto approveQuarantine(String bookingIdentifier, ApproveMedicalRequestDto request, String username) {
        Booking booking = findBookingByIdentifier(bookingIdentifier);
        requireAssignment(username, booking);
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);

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

        BookingHistory.appendHistory(payload, reviewer, "Chuyên viên kiểm dịch đã duyệt ĐẠT xác nhận dịch tễ cho chuyến đi: " + note);

        savePayload(booking, payload);
        Booking saved = bookingRepository.save(booking);
        log.info("Specialist [{}] approved quarantine for booking ID={}", reviewer, saved.getId());
        return mapToBookingResponse(saved);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Booking findBookingByIdentifier(String bookingIdentifier) {
        return findBookingByIdentifier(bookingIdentifier, false);
    }

    private Booking findBookingByIdentifier(String bookingIdentifier, boolean forUpdate) {
        if (bookingIdentifier == null || bookingIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }
        Integer id = parseBookingId(bookingIdentifier);
        return (forUpdate ? bookingRepository.findByIdForUpdate(id) : bookingRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
    }

    private Integer parseBookingId(String bookingIdentifier) {
        return bookingRepository.findIdByCode(bookingIdentifier.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
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
        Object intakeValue = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking).getIntake();
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

    private void savePayload(Booking booking, BookingDetailsPayloadDto payload) {
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
    private Map<String, Object> getOrCreateClearanceMap(BookingDetailsPayloadDto payload, Booking booking) {
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

        boolean hasRequirementSnapshot = payload.getDocumentRequirements() != null;
        String configuredDestinationCountry = payload.getDestCountry() != null ? payload.getDestCountry()
                : borderGate != null ? borderGate.getDestinationCountry() : null;
        List<Map<String, Object>> items = ClearanceDocumentPolicy.buildItems(
                documentRequirementRepository.findByDocumentGroupAndStatusOrderByDocumentNameAsc("CLEARANCE", "ACTIVE"),
                isInternational, borderGate, configuredDestinationCountry, payload.getDocumentRequirements());

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


    private BookingResponseDto mapToBookingResponse(Booking b) {
        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, b);
        Object clearance = ClearanceDocumentPolicy.forBooking(
                payload.getClearance(), "international".equalsIgnoreCase(b.getBookingType()));
        String customerFullName = (b.getCustomerID() != null && b.getCustomerID().getUser() != null)
                ? b.getCustomerID().getUser().getUsername()
                : "Khách hàng";

        return BookingResponseDto.builder()
                .id(b.getCode())
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
                .trips(bookingVisibility.visibleTrips(payload))
                .route(payload.getRoute())
                .gate(payload.getGate())
                .plan(payload.getPlan())
                .photoEvidence(bookingVisibility.visibleEvidence(payload))
                .quote(payload.getQuote())
                .payment(payload.getPayment())
                .medical(payload.getMedical())
                .clearance(clearance)
                .intake(payload.getIntake())
                .rejection(payload.getRejection())
                .sendBack(payload.getSendBack())
                .history(payload.getHistory())
                .build();
    }
}
