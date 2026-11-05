package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.incident.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.SystemLog;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.service.IncidentService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private final com.example.racehorse_transport.service.CaptureService captureService;
    private final IncidentRepository incidentRepository;
    private final BookingRepository bookingRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;
    private final StaffRepository staffRepository;
    private final SystemLogRepository systemLogRepository;
    private final com.example.racehorse_transport.service.HorseHealthLogService horseHealthLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public IncidentResponse reportIncident(ReportIncidentRequest request, String actor, String role) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + request.getBookingId()));

        ensureIncidentBookingStatus(booking);
        Staff reporter = staffRepository.findByUser_Username(actor).orElse(null);
        if ("DRIVER".equals(role) || "ESCORT".equals(role)) {
            if (reporter == null) {
                throw new AccessDeniedException("Không tìm thấy nhân viên đang đăng nhập.");
            }
            ensureAssignedToBooking(booking.getId(), reporter);
        }
        ensureTripBelongsToBooking(booking, request.getTripId());
        if (incidentRepository.findByBookingID_Id(booking.getId()).stream().anyMatch(i ->
                Objects.equals(i.getTripId(), request.getTripId()) && !"resolved".equalsIgnoreCase(i.getStatus())))
            throw new IllegalArgumentException("Chuyến đang có SOS chưa giải quyết; hãy tiếp tục xử lý sự cố hiện tại.");

        BookingDetailsPayload photoPayload = BookingDetailsJson.read(objectMapper, booking);
        captureService.attach(photoPayload, booking.getId(), request.getTripId(), "SOS", "", request.getPhotoUrl(), actor);
        booking.setDetailsJson(writeJson(photoPayload));
        Incident incident = Incident.builder()
                .bookingID(booking)
                .staffID(reporter)
                .tripId(request.getTripId())
                .photoUrl(request.getPhotoUrl())
                .locationLat(request.getLatitude())
                .locationLng(request.getLongitude())
                .incidentType(request.getIncidentType())
                .incidentName(request.getIncidentName())
                .description(request.getDescription())
                .status("reported")
                .createdAt(Instant.now())
                .build();

        if ("DRIVER".equals(role) || "ESCORT".equals(role)) ensureActorAssignedToBooking(incident, actor, role);
        if ("HORSE_HEALTH".equalsIgnoreCase(request.getIncidentType())) {
            var trip = incidentTrip(incident, photoPayload);
            if (request.getHorseIds() == null || request.getHorseIds().isEmpty()
                    || !new HashSet<>(trip.getHorseIds()).containsAll(request.getHorseIds())) {
                throw new IllegalArgumentException("Chọn ngựa bị ảnh hưởng thuộc chuyến xe này.");
            }
            incident.setPlanJson(writeJson(Map.of("horseIds", request.getHorseIds().stream().distinct().toList())));
        }
        Incident saved = incidentRepository.save(incident);

        appendBookingHistory(booking, actor,
                "Báo cáo sự cố khẩn cấp: " + request.getIncidentName() + " (" + request.getIncidentType() + ")");
        refreshBookingIncidentStatus(booking, saved);
        bookingRepository.save(booking);

        logAction("INCIDENT_REPORTED", booking.getId(),
                "Phát hiện sự cố INC-" + saved.getId() + " cho đơn BK-" + booking.getId() + ": " + request.getIncidentName());

        log.info("Incident ID={} reported for Booking ID={}", saved.getId(), booking.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse proposeExternalPlan(Integer incidentId, ProposeExternalPlanRequest request, String actor) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố ID: " + incidentId));

        if ("active".equalsIgnoreCase(incident.getStatus()) && "HORSE_HEALTH".equalsIgnoreCase(incident.getIncidentType())) {
            var trip = incidentTrip(incident, BookingDetailsJson.read(objectMapper, incident.getBookingID()));
            if (tripCheckpoints(trip).stream().anyMatch(cp -> Objects.equals(cp.get("id"), "sos-" + incidentId)))
                throw new IllegalArgumentException("Phương án đang thực hiện không thể lập lại.");
        } else {
            requireStatus(incident, "reported", "open");
        }
        if (request.getResolutionType() != null
                && !Set.of("REROUTE", "EXTERNAL_PARTNER", "FORCE_MAJEURE")
                        .contains(request.getResolutionType().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Loại phương án xử lý không hợp lệ.");
        }
        if (request.getCostBearer() != null
                && !Set.of("COMPANY", "CUSTOMER").contains(request.getCostBearer().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Bên chịu chi phí không hợp lệ.");
        }
        if ("REROUTE".equalsIgnoreCase(request.getResolutionType())) {
            validateReroute(request.getPlan());
        }
        incident.setResolutionType(request.getResolutionType() != null
                ? request.getResolutionType().toUpperCase(Locale.ROOT)
                : "EXTERNAL_PARTNER");
        incident.setPartnerName(request.getPartnerName());
        incident.setPartnerService(request.getPartnerService());
        incident.setPartnerPhone(request.getPartnerPhone());
        incident.setEstimatedCost(request.getEstimatedCost());
        incident.setCostBearer(request.getCostBearer() != null
                ? request.getCostBearer().toUpperCase(Locale.ROOT)
                : "COMPANY");
        incident.setStatus("proposed");
        incident.setFitConfirmedAt(null);
        incident.setFitConfirmedBy(null);
        incident.setRejectionReason(null);
        incident.setRejectionAt(null);
        incident.setRejectionBy(null);
        if (request.getPlan() != null) {
            Map<String, Object> plan = new LinkedHashMap<>(request.getPlan());
            Map<String, Object> previous = readPlan(incident.getPlanJson());
            if (previous != null && previous.get("horseIds") != null) plan.put("horseIds", previous.get("horseIds"));
            if ("HORSE_HEALTH".equalsIgnoreCase(incident.getIncidentType())) validateStationPlan(plan);
            plan.put("at", Instant.now().toEpochMilli());
            plan.put("by", actor);
            incident.setPlanJson(writeJson(plan));
        }

        if (request.getPlan() == null && request.getDescription() != null && !request.getDescription().isBlank()) {
            String updatedDesc = (incident.getDescription() != null ? incident.getDescription() + " | " : "")
                    + "Đề xuất Đội ngoài: " + request.getDescription();
            incident.setDescription(updatedDesc);
        }

        Incident saved = incidentRepository.save(incident);

        Booking booking = incident.getBookingID();
        if (booking != null) {
            booking.setStatus("pending_emergency_approval");
            String partner = request.getPartnerName() != null ? request.getPartnerName() : "phương án trên bản đồ";
            String service = request.getPartnerService() != null ? request.getPartnerService() : "điều chỉnh lộ trình";
            String planDesc = "Đề xuất phương án [" + partner + "] (" + service
                    + ") - Dự kiến: " + (request.getEstimatedCost() != null ? request.getEstimatedCost() + " VND" : "Chưa có");
            appendBookingHistory(booking, actor,
                    planDesc);
            refreshBookingIncidentStatus(booking, saved);
            bookingRepository.save(booking);

            logAction("INCIDENT_PLAN_PROPOSED", booking.getId(),
                    "Điều phối viên đề xuất phương án đội ngoài cho sự cố INC-" + saved.getId() + ": " + planDesc);
        }

        log.info("Proposed external partner plan for Incident ID={}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse approveIncidentPlan(Integer incidentId, ApproveIncidentPlanRequest request, String actor) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố ID: " + incidentId));

        requireStatus(incident, "proposed");
        if (request.getCostBearer() != null
                && !Set.of("COMPANY", "CUSTOMER").contains(request.getCostBearer().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Bên chịu chi phí không hợp lệ.");
        }
        if ("REROUTE".equalsIgnoreCase(incident.getResolutionType())) {
            validateReroute(readPlan(incident.getPlanJson()));
            applyReroute(incident, actor);
        }
        if ("HORSE_HEALTH".equalsIgnoreCase(incident.getIncidentType())) {
            applyStationPlan(incident, actor);
        }
        incident.setStatus("active");
        incident.setDirective(request.getDirective());
        incident.setApprovedAt(Instant.now());
        incident.setApprovedBy(actor);
        if (request.getApprovedCost() != null) {
            incident.setEstimatedCost(request.getApprovedCost());
        }
        if (request.getCostBearer() != null) {
            incident.setCostBearer(request.getCostBearer().toUpperCase(Locale.ROOT));
        }

        if ("HORSE_HEALTH".equalsIgnoreCase(incident.getIncidentType())) {
            Map<String, Object> approvedPlan = new LinkedHashMap<>(readPlan(incident.getPlanJson()));
            approvedPlan.put("approvedCostBearer", incident.getCostBearer());
            incident.setPlanJson(writeJson(approvedPlan));
        }
        Incident saved = incidentRepository.save(incident);

        Booking booking = incident.getBookingID();
        if (booking != null) {
            booking.setStatus("emergency_plan_active");
            String text = "Quản lý phê duyệt phương án: " + (request.getDirective() != null ? request.getDirective() : "Đồng ý phương án cứu hộ/thú y ngoài.")
                    + " (Bên chịu chi phí: " + ("CUSTOMER".equalsIgnoreCase(saved.getCostBearer()) ? "Khách hàng" : "Nhà xe") + ")";
            appendBookingHistory(booking, actor,
                    text);
            refreshBookingIncidentStatus(booking, saved);
            bookingRepository.save(booking);

            logAction("INCIDENT_PLAN_APPROVED", booking.getId(),
                    "Quản lý duyệt phương án cho sự cố INC-" + saved.getId() + ": " + text);
        }

        log.info("Manager approved plan for Incident ID={}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse rejectIncidentPlan(Integer incidentId, RejectIncidentPlanRequest request, String actor) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố ID: " + incidentId));
        requireStatus(incident, "proposed");
        incident.setStatus("reported");
        incident.setRejectionReason(request.getReason().trim());
        incident.setRejectionAt(Instant.now());
        incident.setRejectionBy(actor);
        Incident saved = incidentRepository.save(incident);
        Booking booking = incident.getBookingID();
        if (booking != null) {
            appendBookingHistory(booking, actor,
                    "Trả phương án sự cố INC-" + incidentId + ": " + request.getReason().trim());
            refreshBookingIncidentStatus(booking, saved);
            bookingRepository.save(booking);
        }
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse addExpense(Integer incidentId, IncidentExpenseRequest request, String actor, String role) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố ID: " + incidentId));
        requireStatus(incident, "active");
        ensureActorAssignedToBooking(incident, actor, role);
        if (!Set.of("vet_fee", "medicine", "holding_stable", "rescue", "repair", "other")
                .contains(request.getCategory().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Loại chi phí không hợp lệ.");
        }
        if (!List.of("customer", "carrier").contains(request.getPayer().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Bên chịu chi phí không hợp lệ.");
        }
        BookingDetailsPayload photoPayload = BookingDetailsJson.read(objectMapper, incident.getBookingID());
        captureService.attach(photoPayload, incident.getBookingID().getId(), incident.getTripId(), "EXPENSE", "", request.getPhoto(), actor);
        incident.getBookingID().setDetailsJson(writeJson(photoPayload));
        bookingRepository.save(incident.getBookingID());
        List<Map<String, Object>> expenses = readExpenses(incident.getExpensesJson());
        Map<String, Object> expense = new LinkedHashMap<>();
        expense.put("id", UUID.randomUUID().toString());
        expense.put("category", request.getCategory());
        expense.put("label", request.getLabel().trim());
        expense.put("photo", request.getPhoto());
        expense.put("amount", request.getAmount());
        String payer = request.getPayer().toLowerCase(Locale.ROOT);
        if ("HORSE_HEALTH".equalsIgnoreCase(incident.getIncidentType())) {
            Map<String, Object> plan = readPlan(incident.getPlanJson());
            String bearer = plan != null && plan.get("approvedCostBearer") != null
                    ? String.valueOf(plan.get("approvedCostBearer")) : incident.getCostBearer();
            payer = "CUSTOMER".equalsIgnoreCase(bearer) ? "customer" : "carrier";
        }
        expense.put("payer", payer);
        expense.put("at", Instant.now().toEpochMilli());
        expense.put("by", actor);
        expenses.add(expense);
        incident.setExpensesJson(writeJson(expenses));
        BigDecimal customerTotal = expenses.stream()
                .filter(item -> "customer".equalsIgnoreCase(String.valueOf(item.get("payer"))))
                .map(item -> new BigDecimal(String.valueOf(item.get("amount"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        incident.setActualCost(customerTotal);
        incident.setCostBearer(customerTotal.compareTo(BigDecimal.ZERO) > 0 ? "CUSTOMER" : "COMPANY");
        Incident saved = incidentRepository.save(incident);
        Booking booking = incident.getBookingID();
        if (booking != null) {
            appendBookingHistory(booking, actor, "Ghi nhận chi phí sự cố INC-" + incidentId + ": "
                    + request.getLabel().trim() + " (" + request.getAmount() + " VND)");
            bookingRepository.save(booking);
        }
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse confirmFit(Integer incidentId, ConfirmIncidentFitRequest request, String actor, String role) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố ID: " + incidentId));
        requireStatus(incident, "active");
        ensureActorAssignedToBooking(incident, actor, role);
        if ("TRAFFIC_JAM".equalsIgnoreCase(incident.getIncidentType())
                || "TRAFFIC_DELAY".equalsIgnoreCase(incident.getIncidentType())) {
            throw new IllegalArgumentException("Sự cố giao thông không yêu cầu xác nhận sức khỏe ngựa.");
        }
        if (incident.getFitConfirmedAt() != null) {
            throw new IllegalArgumentException("Sức khỏe ngựa đã được xác nhận.");
        }
        if (!"ADMIN".equals(role) && !"ESCORT".equals(role)) throw new AccessDeniedException("Chỉ hộ tống được xác nhận sức khỏe.");
        if ("HORSE_HEALTH".equalsIgnoreCase(incident.getIncidentType())) {
            BookingDetailsPayload payload = BookingDetailsJson.read(objectMapper, incident.getBookingID());
            Map<String, Object> checkpoint = stationCheckpoint(incident, payload);
            if (checkpoint.get("arrivedAt") == null) throw new IllegalArgumentException("Cần xác nhận tới trạm SOS trước khi khám.");
            if (request == null || request.getHorses() == null || request.getHorses().isEmpty())
                throw new IllegalArgumentException("Cần ghi kết quả chăm sóc từng ngựa bị ảnh hưởng.");
            var expected = affectedHorseIds(incident);
            var actual = request.getHorses().stream().map(ConfirmIncidentFitRequest.CareRecord::getHorseId).toList();
            if (actual.size() != new HashSet<>(actual).size() || !new HashSet<>(actual).equals(new HashSet<>(expected)))
                throw new IllegalArgumentException("Cần ghi đủ và đúng các ngựa bị ảnh hưởng.");
            Staff escort = staffRepository.findByUser_Username(actor).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hộ tống."));
            for (var care : request.getHorses()) {
                if (care.getCareNote() == null || care.getCareNote().isBlank()) throw new IllegalArgumentException("Cần mô tả kết quả chăm sóc.");
                Integer horseId = Integer.valueOf(care.getHorseId().replaceAll("[^0-9]", ""));
                horseHealthLogService.updateHorseHealthStatusByEscort(horseId, incident.getBookingID().getId(),
                        "ĐỦ SỨC ĐI TIẾP SAU SOS", care.getTemperature(), null,
                        "INC-" + incidentId + ": " + care.getCareNote().trim(), escort.getId());
            }
            if (request.getPhoto() == null || request.getPhoto().isBlank()) throw new IllegalArgumentException("Cần ảnh kết quả chăm sóc.");
            captureService.attach(payload, incident.getBookingID().getId(), incident.getTripId(), "SUBMIT_WELFARE",
                    "sos-" + incidentId, request.getPhoto(), actor);
            var trip = incidentTrip(incident, payload);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> welfare = (List<Map<String, Object>>) trip.getRun().computeIfAbsent("welfare", k -> new ArrayList<>());
            welfare.add(new LinkedHashMap<>(Map.of("id", "sos-care-" + incidentId, "checkpointId", "sos-" + incidentId,
                    "condition", "normal", "waterLiters", Objects.requireNonNullElse(request.getWaterLiters(), BigDecimal.ZERO), "hay", request.isHay(), "photo", request.getPhoto(), "at", Instant.now().toEpochMilli(), "by", actor,
                    "note", "Đã chăm sóc và xác nhận đủ sức đi tiếp")));
            incident.getBookingID().setDetailsJson(writeJson(payload));
            bookingRepository.save(incident.getBookingID());
            Map<String, Object> plan = new LinkedHashMap<>(readPlan(incident.getPlanJson()));
            plan.put("careRecords", request.getHorses());
            incident.setPlanJson(writeJson(plan));
        }
        incident.setFitConfirmedAt(Instant.now());
        incident.setFitConfirmedBy(actor);
        return mapToResponse(incidentRepository.save(incident));
    }

    @Override
    @Transactional
    public IncidentResponse resumeJourney(Integer incidentId, String actor, String role) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố ID: " + incidentId));
        requireStatus(incident, "active");
        ensureActorAssignedToBooking(incident, actor, role);
        if (!"TRAFFIC_JAM".equalsIgnoreCase(incident.getIncidentType())
                && !"TRAFFIC_DELAY".equalsIgnoreCase(incident.getIncidentType())
                && incident.getFitConfirmedAt() == null) {
            throw new IllegalArgumentException("Cần xác nhận ngựa đủ sức đi tiếp trước khi tiếp tục hành trình.");
        }
        if ("HORSE_HEALTH".equalsIgnoreCase(incident.getIncidentType())) finishStation(incident);
        incident.setStatus("resolved");
        incident.setResolvedAt(Instant.now());
        Incident saved = incidentRepository.save(incident);
        Booking booking = incident.getBookingID();
        if (booking != null) {
            appendBookingHistory(booking, actor,
                    "Kíp xe tiếp tục hành trình sau khi xử lý sự cố INC-" + incidentId + ".");
            refreshBookingIncidentStatus(booking, saved);
            bookingRepository.save(booking);
            logAction("INCIDENT_RESOLVED", booking.getId(),
                    "Đã giải quyết sự cố INC-" + saved.getId() + " và tiếp tục hành trình");
        }
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse completeResolution(Integer incidentId, ResolveIncidentRequest request, String actor) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố ID: " + incidentId));

        requireStatus(incident, "active");
        if ("HORSE_HEALTH".equalsIgnoreCase(incident.getIncidentType()))
            throw new IllegalArgumentException("SOS sức khỏe phải được hộ tống xác nhận và tài xế rời trạm qua luồng tiếp tục hành trình.");
        if (request.getActualCost() != null && request.getActualCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Chi phí thực tế không được âm.");
        }
        incident.setStatus("resolved");
        incident.setResolvedAt(Instant.now());
        if (request.getActualCost() != null) {
            incident.setActualCost(request.getActualCost());
        }
        if (request.getInvoiceUrl() != null) {
            incident.setInvoiceUrl(request.getInvoiceUrl());
        }

        Incident saved = incidentRepository.save(incident);

        Booking booking = incident.getBookingID();
        if (booking != null) {
            String resolveText = "Đội ngoài xử lý hoàn tất sự cố. Đã lưu hóa đơn thực tế: "
                    + (saved.getActualCost() != null ? saved.getActualCost() + " VND" : "0 VND")
                    + ". Kíp xe tiếp tục hành trình chính.";
            appendBookingHistory(booking, actor, resolveText);
            refreshBookingIncidentStatus(booking, saved);
            bookingRepository.save(booking);

            logAction("INCIDENT_RESOLVED", booking.getId(),
                    "Đã giải quyết sự cố INC-" + saved.getId() + ", hóa đơn: " + saved.getActualCost() + " VND");
        }

        log.info("Resolved Incident ID={}, actual cost={}", saved.getId(), saved.getActualCost());
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidentsByBooking(Integer bookingId, String requester, String role) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + bookingId));
        if ("CUSTOMER".equals(role)) {
            String owner = booking.getCustomerID() != null && booking.getCustomerID().getUser() != null
                    ? booking.getCustomerID().getUser().getUsername() : null;
            if (!Objects.equals(owner, requester)) {
                throw new AccessDeniedException("Bạn không có quyền xem sự cố của đơn hàng này.");
            }
        } else if ("DRIVER".equals(role) || "ESCORT".equals(role)) {
            Staff staff = staffRepository.findByUser_Username(requester)
                    .orElseThrow(() -> new AccessDeniedException("Không tìm thấy nhân viên đang đăng nhập."));
            ensureAssignedToBooking(bookingId, staff);
        }
        return incidentRepository.findByBookingID_Id(bookingId)
                .stream()
                .map(incident -> "CUSTOMER".equals(role) ? mapToCustomerResponse(incident) : mapToResponse(incident))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getCustomerIncidents(String customerUsername) {
        return incidentRepository.findByBookingID_CustomerID_User_Username(customerUsername)
                .stream()
                .map(this::mapToCustomerResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidentsForBookings(java.util.Collection<Integer> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) return List.of();
        return incidentRepository.findByBookingID_IdIn(bookingIds).stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getAllIncidents(String status) {
        List<Incident> list = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL"))
                ? incidentRepository.findByStatus(status.toLowerCase())
                : incidentRepository.findAll();
        return list.stream().map(this::mapToResponse).toList();
    }

    private com.example.racehorse_transport.dto.coordinator.VehicleTripDto incidentTrip(Incident incident, BookingDetailsPayload payload) {
        if (payload.getTrips() == null) throw new IllegalArgumentException("Đơn chưa có chuyến xe.");
        return payload.getTrips().stream().filter(t -> Objects.equals(t.getTripId(), incident.getTripId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến xe của sự cố."));
    }

    private List<String> affectedHorseIds(Incident incident) {
        Map<String, Object> plan = readPlan(incident.getPlanJson());
        if (plan != null && plan.get("horseIds") instanceof List<?> ids)
            return ids.stream().map(String::valueOf).toList();
        if (!"HORSE_HEALTH".equalsIgnoreCase(incident.getIncidentType()) || incident.getBookingID() == null) return List.of();
        var trip = incidentTrip(incident, BookingDetailsJson.read(objectMapper, incident.getBookingID()));
        return trip.getHorseIds() == null ? List.of() : trip.getHorseIds();
    }

    private void validateStationPlan(Map<String, Object> plan) {
        if (plan == null || !"to_station".equals(plan.get("action"))
                || !(plan.get("station") instanceof String station) || station.isBlank()
                || !(plan.get("restMinutes") instanceof Number minutes) || minutes.intValue() < 30 || minutes.intValue() > 1440
                || !(plan.get("newEta") instanceof Number eta) || eta.longValue() <= 0) {
            throw new IllegalArgumentException("Phương án chăm sóc phải có trạm, giờ đến và thời gian nghỉ ít nhất 30 phút.");
        }
        for (String field : List.of("toStation", "onward")) {
            Map<String, Object> check = new HashMap<>();
            check.put("action", "reroute"); check.put("newEta", plan.get("newEta")); check.put("detour", plan.get(field));
            validateReroute(check);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tripCheckpoints(com.example.racehorse_transport.dto.coordinator.VehicleTripDto trip) {
        if (trip.getRun() == null || !(trip.getRun().get("checkpoints") instanceof List<?>))
            throw new IllegalArgumentException("Chuyến chưa có các mốc hành trình.");
        return (List<Map<String, Object>>) trip.getRun().get("checkpoints");
    }

    private Map<String, Object> stationCheckpoint(Incident incident, BookingDetailsPayload payload) {
        return tripCheckpoints(incidentTrip(incident, payload)).stream()
                .filter(cp -> Objects.equals("sos-" + incident.getId(), cp.get("id"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Phương án SOS cũ chưa có mốc trạm; cần điều phối lập lại phương án."));
    }

    private void applyStationPlan(Incident incident, String actor) {
        Map<String, Object> plan = new LinkedHashMap<>(readPlan(incident.getPlanJson()) == null ? Map.of() : readPlan(incident.getPlanJson()));
        validateStationPlan(plan);
        Booking booking = incident.getBookingID();
        BookingDetailsPayload payload = BookingDetailsJson.read(objectMapper, booking);
        var trip = incidentTrip(incident, payload);
        var checkpoints = tripCheckpoints(trip);
        Map<String, Object> next = checkpoints.stream().filter(cp -> cp.get("doneAt") == null).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Chuyến không còn mốc để điều chỉnh."));
        int index = checkpoints.indexOf(next);
        String replacement = Objects.toString(plan.get("replaceRestCheckpointId"), "");
        if (replacement.isBlank() && "rest".equals(next.get("type")) && next.get("arrivedAt") == null
                && Objects.toString(plan.get("station"), "").equalsIgnoreCase(Objects.toString(next.get("place"), ""))) {
            replacement = String.valueOf(next.get("id"));
            plan.put("replaceRestCheckpointId", replacement);
        }
        if (!replacement.isBlank()) {
            if (!replacement.equals(next.get("id")) || !"rest".equals(next.get("type")) || next.get("arrivedAt") != null)
                throw new IllegalArgumentException("Chỉ thay thế mốc nghỉ kế tiếp chưa check-in, không được bỏ cửa khẩu hoặc điểm giao.");
            next.put("supersededByIncidentId", incident.getId());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> archived = (List<Map<String, Object>>) trip.getRun().computeIfAbsent("supersededCheckpoints", k -> new ArrayList<>());
            archived.add(next);
            checkpoints.remove(index);
        } else if ("rest".equals(next.get("type")) && next.get("arrivedAt") != null) {
            next.put("interruptedByIncidentId", incident.getId());
        }
        Map<String, Object> checkpoint = new LinkedHashMap<>();
        checkpoint.put("id", "sos-" + incident.getId()); checkpoint.put("type", "rest");
        checkpoint.put("label", "Trạm chăm sóc SOS"); checkpoint.put("place", plan.get("station"));
        checkpoint.put("incidentId", incident.getId()); checkpoint.put("restMinutes", plan.get("restMinutes"));
        Map<?, ?> line = (Map<?, ?>) plan.get("toStation");
        Number hours = line.get("hours") instanceof Number n ? n : 0;
        checkpoint.put("plannedAt", Instant.now().toEpochMilli() + (long) (hours.doubleValue() * 3600000));
        // Dời lịch các mốc chưa tới theo độ trễ của ETA mới; giữ nguyên thời gian thực tế đã ghi nhận.
        Long originalEta = checkpoints.stream().filter(cp -> "delivery".equals(cp.get("type")))
                .map(cp -> cp.get("plannedAt")).filter(Number.class::isInstance).map(Number.class::cast)
                .map(Number::longValue).findFirst().orElse(null);
        if (originalEta != null) {
            long delay = ((Number) plan.get("newEta")).longValue() - originalEta;
            for (var cp : checkpoints) if (cp.get("doneAt") == null && cp.get("arrivedAt") == null && cp.get("plannedAt") instanceof Number at)
                cp.put("plannedAt", at.longValue() + delay);
        }
        checkpoints.add(index, checkpoint);
        for (var cp : checkpoints) if ("delivery".equals(cp.get("type")) && cp.get("arrivedAt") == null && cp.get("doneAt") == null)
            cp.put("plannedAt", plan.get("newEta"));
        plan.put("horseIds", affectedHorseIds(incident));
        plan.put("incidentId", incident.getId()); plan.put("approvedAt", Instant.now().toEpochMilli()); plan.put("approvedBy", actor);
        trip.setIncidentRoute(plan);
        incident.setPlanJson(writeJson(plan));
        booking.setDetailsJson(writeJson(payload));
    }

    private void finishStation(Incident incident) {
        Booking booking = incident.getBookingID();
        BookingDetailsPayload payload = BookingDetailsJson.read(objectMapper, booking);
        var trip = incidentTrip(incident, payload);
        Map<String, Object> checkpoint = stationCheckpoint(incident, payload);
        if (!(checkpoint.get("arrivedAt") instanceof Number arrival) || incident.getFitConfirmedAt() == null)
            throw new IllegalArgumentException("Cần tới trạm và được hộ tống xác nhận đủ sức trước khi rời trạm.");
        Number minutes = (Number) checkpoint.get("restMinutes");
        long now = Instant.now().toEpochMilli();
        if (now < arrival.longValue() + minutes.longValue() * 60000)
            throw new IllegalArgumentException("Chưa đủ thời gian nghỉ đã duyệt tại trạm SOS.");
        checkpoint.put("doneAt", now); checkpoint.put("leftAt", now);
        for (var cp : tripCheckpoints(trip)) if (Objects.equals(cp.get("interruptedByIncidentId"), incident.getId()) && cp.get("doneAt") == null) {
            cp.put("doneAt", now); cp.put("leftAt", now); cp.put("completionReason", "Đã hoàn tất chăm sóc tại trạm SOS");
        }
        booking.setDetailsJson(writeJson(payload));
    }

    private void validateReroute(Map<String, Object> plan) {
        if (plan == null || !"reroute".equals(plan.get("action"))
                || !(plan.get("newEta") instanceof Number eta) || eta.longValue() <= 0
                || !(plan.get("detour") instanceof Map<?, ?> detour)
                || !(detour.get("path") instanceof List<?> path) || path.size() < 2) {
            throw new IllegalArgumentException("Phương án đổi tuyến phải có đường đi và giờ đến mới hợp lệ.");
        }
        for (Object item : path) {
            if (!(item instanceof List<?> point) || point.size() != 2
                    || !(point.get(0) instanceof Number lat) || !(point.get(1) instanceof Number lng)
                    || !Double.isFinite(lat.doubleValue()) || !Double.isFinite(lng.doubleValue())
                    || Math.abs(lat.doubleValue()) > 90 || Math.abs(lng.doubleValue()) > 180) {
                throw new IllegalArgumentException("Tọa độ đường tránh không hợp lệ.");
            }
        }
    }

    private void applyReroute(Incident incident, String actor) {
        Booking booking = incident.getBookingID();
        if (booking == null) throw new IllegalArgumentException("Sự cố không liên kết với đơn hàng.");
        BookingDetailsPayload payload = BookingDetailsJson.read(objectMapper, booking);
        var trip = payload.getTrips() == null ? null : payload.getTrips().stream()
                .filter(item -> Objects.equals(item.getTripId(), incident.getTripId())).findFirst().orElse(null);
        if (trip == null) throw new IllegalArgumentException("Không tìm thấy chuyến xe cần đổi tuyến.");
        Map<String, Object> route = new LinkedHashMap<>(readPlan(incident.getPlanJson()));
        route.put("incidentId", incident.getId());
        route.put("approvedAt", Instant.now().toEpochMilli());
        route.put("approvedBy", actor);
        trip.setIncidentRoute(route);
        // Chỉ cập nhật mốc giao hàng chưa hoàn tất của chuyến gặp sự cố.
        if (trip.getRun() != null && trip.getRun().get("checkpoints") instanceof List<?> checkpoints) {
            for (Object item : checkpoints) {
                if (item instanceof Map<?, ?> checkpoint && "delivery".equals(checkpoint.get("type"))
                        && checkpoint.get("arrivedAt") == null && checkpoint.get("doneAt") == null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mutable = (Map<String, Object>) checkpoint;
                    mutable.put("plannedAt", route.get("newEta"));
                }
            }
        }
        booking.setDetailsJson(writeJson(payload));
    }

    private void ensureIncidentBookingStatus(Booking booking) {
        String status = booking.getStatus() == null ? "" : booking.getStatus().toLowerCase(Locale.ROOT);
        if (!Set.of("in_transit", "incident_reported", "pending_emergency_approval", "emergency_plan_active")
                .contains(status)) {
            throw new IllegalArgumentException("Chỉ có thể báo sự cố khi chuyến đang vận chuyển hoặc đang xử lý sự cố.");
        }
    }

    private void ensureAssignedToBooking(Integer bookingId, Staff staff) {
        boolean assigned = bookingAssignmentRepository.findByBookingID_Id(bookingId).stream()
                .anyMatch(assignment -> assignment.getStaffID() != null
                        && Objects.equals(assignment.getStaffID().getId(), staff.getId()));
        if (!assigned) {
            throw new AccessDeniedException("Bạn không thuộc kíp xe được phân công cho đơn hàng này.");
        }
    }

    private void ensureActorAssignedToBooking(Incident incident, String actor, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        Staff staff = staffRepository.findByUser_Username(actor)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy nhân viên đang đăng nhập."));
        if (incident.getBookingID() == null) {
            throw new IllegalArgumentException("Sự cố không liên kết với đơn hàng.");
        }
        ensureAssignedToBooking(incident.getBookingID().getId(), staff);
        var trip = incidentTrip(incident, BookingDetailsJson.read(objectMapper, incident.getBookingID()));
        String assigned = "ESCORT".equals(role) ? trip.getEscortId() : trip.getDriverId();
        if (!Objects.equals(assigned, staff.getStaffCode()) && !Objects.equals(assigned, String.valueOf(staff.getId())))
            throw new AccessDeniedException("Bạn không thuộc kíp xe của sự cố này.");
    }

    private void ensureTripBelongsToBooking(Booking booking, String tripId) {
        if (booking.getDetailsJson() == null || booking.getDetailsJson().isBlank()) {
            throw new IllegalArgumentException("Đơn hàng chưa có dữ liệu chuyến xe để báo sự cố.");
        }
        try {
            BookingDetailsPayload payload = objectMapper.readValue(booking.getDetailsJson(), BookingDetailsPayload.class);
            boolean belongs = payload.getTrips() != null && payload.getTrips().stream()
                    .anyMatch(trip -> Objects.equals(trip.getTripId(), tripId));
            if (!belongs) {
                throw new IllegalArgumentException("Chuyến xe không thuộc đơn hàng này.");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Không thể xác minh chuyến xe của đơn hàng.", ex);
        }
    }

    private void requireStatus(Incident incident, String... statuses) {
        String current = normalizedStatus(incident);
        boolean allowed = Arrays.stream(statuses).anyMatch(status -> status.equalsIgnoreCase(current));
        if (!allowed) {
            throw new IllegalArgumentException("Sự cố đang ở trạng thái '" + current
                    + "' nên không thể thực hiện thao tác này.");
        }
    }

    private void refreshBookingIncidentStatus(Booking booking, Incident changedIncident) {
        List<Incident> incidents = new ArrayList<>(incidentRepository.findByBookingID_Id(booking.getId()));
        incidents.removeIf(existing -> Objects.equals(existing.getId(), changedIncident.getId()));
        incidents.add(changedIncident);
        boolean reported = incidents.stream()
                .anyMatch(i -> Set.of("open", "reported").contains(normalizedStatus(i)));
        boolean proposed = incidents.stream()
                .anyMatch(i -> Set.of("proposed", "pending_approval").contains(normalizedStatus(i)));
        boolean active = incidents.stream().anyMatch(i -> "active".equals(normalizedStatus(i)));
        if (reported) {
            booking.setStatus("incident_reported");
        } else if (proposed) {
            booking.setStatus("pending_emergency_approval");
        } else if (active) {
            booking.setStatus("emergency_plan_active");
        } else if (Set.of("in_transit", "incident_reported", "pending_emergency_approval", "emergency_plan_active")
                .contains(booking.getStatus() == null ? "" : booking.getStatus().toLowerCase(Locale.ROOT))) {
            booking.setStatus("in_transit");
        }
    }

    private String normalizedStatus(Incident incident) {
        return incident.getStatus() == null ? "" : incident.getStatus().toLowerCase(Locale.ROOT);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể lưu dữ liệu sự cố.", ex);
        }
    }

    private Map<String, Object> readPlan(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể đọc phương án sự cố.", ex);
        }
    }

    private List<Map<String, Object>> readExpenses(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể đọc chi phí sự cố.", ex);
        }
    }

    private void appendBookingHistory(Booking booking, String actor, String text) {
        try {
            BookingDetailsPayload payload = null;
            if (booking.getDetailsJson() != null && !booking.getDetailsJson().isBlank()) {
                if (booking.getDetailsJson().trim().startsWith("{")) {
                    payload = objectMapper.readValue(booking.getDetailsJson(), BookingDetailsPayload.class);
                }
            }
            if (payload == null) {
                payload = new BookingDetailsPayload();
            }
            if (payload.getHistory() == null) {
                payload.setHistory(new ArrayList<>());
            }
            Map<String, Object> entry = new HashMap<>();
            entry.put("time", System.currentTimeMillis());
            entry.put("actor", actor);
            entry.put("text", text);
            payload.getHistory().add(entry);

            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.warn("Could not append history to detailsJson: {}", ex.getMessage());
        }
    }

    private void logAction(String actionType, Integer bookingId, String newData) {
        try {
            SystemLog sysLog = SystemLog.builder()
                    .actionType(actionType)
                    .targetTable("INCIDENT")
                    .targetRecordID(bookingId)
                    .newData(newData)
                    .createdAt(Instant.now())
                    .build();
            systemLogRepository.save(sysLog);
        } catch (Exception ex) {
            // Nhật ký lỗi không được làm hỏng thao tác chính, nhưng phải để lại dấu vết
            log.warn("Không ghi được nhật ký hệ thống {}: {}", actionType, ex.getMessage());
        }
    }

    private IncidentResponse mapToResponse(Incident inc) {
        String bookingCode = inc.getBookingID() != null ? "BK-" + String.format("%03d", inc.getBookingID().getId()) : null;
        Integer bookingId = inc.getBookingID() != null ? inc.getBookingID().getId() : null;
        String reporter = inc.getStaffID() != null && inc.getStaffID().getUser() != null
                ? inc.getStaffID().getUser().getUsername() : null;

        return IncidentResponse.builder()
                .id(inc.getId())
                .formattedId("INC-" + String.format("%03d", inc.getId()))
                .bookingId(bookingId)
                .bookingCode(bookingCode)
                .reporter(reporter)
                .tripId(inc.getTripId())
                .horseIds(affectedHorseIds(inc))
                .location(inc.getLocationLat() != null && inc.getLocationLng() != null
                        ? Map.of("lat", inc.getLocationLat(), "lng", inc.getLocationLng()) : null)
                .photoUrl(inc.getPhotoUrl())
                .incidentName(inc.getIncidentName())
                .incidentType(inc.getIncidentType())
                .description(inc.getDescription())
                .status(inc.getStatus())
                .resolutionType(inc.getResolutionType())
                .partnerName(inc.getPartnerName())
                .partnerService(inc.getPartnerService())
                .partnerPhone(inc.getPartnerPhone())
                .estimatedCost(inc.getEstimatedCost())
                .actualCost(inc.getActualCost())
                .costBearer(inc.getCostBearer())
                .invoiceUrl(inc.getInvoiceUrl())
                .directive(inc.getDirective())
                .plan(readPlan(inc.getPlanJson()))
                .expenses(readExpenses(inc.getExpensesJson()))
                .rejection(inc.getRejectionReason() != null
                        ? Map.of("reason", inc.getRejectionReason(),
                                "at", inc.getRejectionAt() != null ? inc.getRejectionAt().toEpochMilli() : 0L,
                                "by", inc.getRejectionBy() != null ? inc.getRejectionBy() : "")
                        : null)
                .approval(inc.getApprovedAt() != null
                        ? Map.of("at", inc.getApprovedAt().toEpochMilli(),
                                "by", inc.getApprovedBy() != null ? inc.getApprovedBy() : "")
                        : null)
                .fitConfirmedAt(inc.getFitConfirmedAt() != null ? inc.getFitConfirmedAt().toEpochMilli() : null)
                .newEta(planEta(inc))
                .createdAt(inc.getCreatedAt() != null ? inc.getCreatedAt().toEpochMilli() : null)
                .resolvedAt(inc.getResolvedAt() != null ? inc.getResolvedAt().toEpochMilli() : null)
                .build();
    }

    private IncidentResponse mapToCustomerResponse(Incident incident) {
        Integer bookingId = incident.getBookingID() != null ? incident.getBookingID().getId() : null;
        String bookingCode = bookingId != null ? "BK-" + String.format("%03d", bookingId) : null;
        return IncidentResponse.builder()
                .id(incident.getId())
                .formattedId("INC-" + String.format("%03d", incident.getId()))
                .bookingId(bookingId)
                .bookingCode(bookingCode)
                .incidentType(incident.getIncidentType())
                .status(incident.getStatus())
                .newEta(planEta(incident))
                .createdAt(incident.getCreatedAt() != null ? incident.getCreatedAt().toEpochMilli() : null)
                .resolvedAt(incident.getResolvedAt() != null ? incident.getResolvedAt().toEpochMilli() : null)
                .build();
    }

    private Long planEta(Incident incident) {
        Map<String, Object> plan = readPlan(incident.getPlanJson());
        Object value = plan != null ? plan.get("newEta") : null;
        return value instanceof Number number ? number.longValue() : null;
    }
}
