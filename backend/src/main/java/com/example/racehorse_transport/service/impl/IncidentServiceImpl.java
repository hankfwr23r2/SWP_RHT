package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.incident.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.SystemLog;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.service.IncidentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidentRepository;
    private final BookingRepository bookingRepository;
    private final StaffRepository staffRepository;
    private final SystemLogRepository systemLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public IncidentResponse reportIncident(ReportIncidentRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + request.getBookingId()));

        Staff reporter = null;
        if (request.getReporterUsername() != null && !request.getReporterUsername().isBlank()) {
            reporter = staffRepository.findByUser_Username(request.getReporterUsername())
                    .or(() -> staffRepository.findByStaffCode(request.getReporterUsername()))
                    .orElse(null);
        }

        Incident incident = Incident.builder()
                .bookingID(booking)
                .staffID(reporter)
                .incidentType(request.getIncidentType())
                .incidentName(request.getIncidentName())
                .description(request.getDescription())
                .status("open")
                .createdAt(Instant.now())
                .build();

        Incident saved = incidentRepository.save(incident);

        // Chuyển trạng thái đơn sang incident_reported
        booking.setStatus("incident_reported");
        appendBookingHistory(booking, request.getReporterUsername() != null ? request.getReporterUsername() : "Kíp xe",
                "Báo cáo sự cố khẩn cấp: " + request.getIncidentName() + " (" + request.getIncidentType() + ")");
        bookingRepository.save(booking);

        logAction("INCIDENT_REPORTED", booking.getId(),
                "Phát hiện sự cố INC-" + saved.getId() + " cho đơn BK-" + booking.getId() + ": " + request.getIncidentName());

        log.info("Incident ID={} reported for Booking ID={}", saved.getId(), booking.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse proposeExternalPlan(Integer incidentId, ProposeExternalPlanRequest request) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sự cố ID: " + incidentId));

        incident.setResolutionType(request.getResolutionType() != null ? request.getResolutionType() : "EXTERNAL_PARTNER");
        incident.setPartnerName(request.getPartnerName());
        incident.setPartnerService(request.getPartnerService());
        incident.setPartnerPhone(request.getPartnerPhone());
        incident.setEstimatedCost(request.getEstimatedCost());
        incident.setCostBearer(request.getCostBearer() != null ? request.getCostBearer() : "COMPANY");
        incident.setStatus("proposed");

        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            String updatedDesc = (incident.getDescription() != null ? incident.getDescription() + " | " : "")
                    + "Đề xuất Đội ngoài: " + request.getDescription();
            incident.setDescription(updatedDesc);
        }

        Incident saved = incidentRepository.save(incident);

        Booking booking = incident.getBookingID();
        if (booking != null) {
            booking.setStatus("pending_emergency_approval");
            String planDesc = "Đề xuất gọi đội ngoài [" + request.getPartnerName() + "] (" + request.getPartnerService()
                    + ") - Dự kiến: " + (request.getEstimatedCost() != null ? request.getEstimatedCost() + " VND" : "Chưa có");
            appendBookingHistory(booking, request.getCoordinatorUsername() != null ? request.getCoordinatorUsername() : "Điều phối viên",
                    planDesc);
            bookingRepository.save(booking);

            logAction("INCIDENT_PLAN_PROPOSED", booking.getId(),
                    "Điều phối viên đề xuất phương án đội ngoài cho sự cố INC-" + saved.getId() + ": " + planDesc);
        }

        log.info("Proposed external partner plan for Incident ID={}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse approveIncidentPlan(Integer incidentId, ApproveIncidentPlanRequest request) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sự cố ID: " + incidentId));

        incident.setStatus("active");
        incident.setDirective(request.getDirective());
        if (request.getApprovedCost() != null) {
            incident.setEstimatedCost(request.getApprovedCost());
        }
        if (request.getCostBearer() != null) {
            incident.setCostBearer(request.getCostBearer());
        }

        Incident saved = incidentRepository.save(incident);

        Booking booking = incident.getBookingID();
        if (booking != null) {
            booking.setStatus("emergency_plan_active");
            String text = "Quản lý phê duyệt phương án: " + (request.getDirective() != null ? request.getDirective() : "Đồng ý phương án cứu hộ/thú y ngoài.")
                    + " (Bên chịu chi phí: " + ("CUSTOMER".equalsIgnoreCase(saved.getCostBearer()) ? "Khách hàng" : "Nhà xe") + ")";
            appendBookingHistory(booking, request.getManagerUsername() != null ? request.getManagerUsername() : "Quản lý",
                    text);
            bookingRepository.save(booking);

            logAction("INCIDENT_PLAN_APPROVED", booking.getId(),
                    "Quản lý duyệt phương án cho sự cố INC-" + saved.getId() + ": " + text);
        }

        log.info("Manager approved plan for Incident ID={}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public IncidentResponse completeResolution(Integer incidentId, ResolveIncidentRequest request) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sự cố ID: " + incidentId));

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
            // Khôi phục trạng thái hành trình tiếp tục
            booking.setStatus("in_transit");
            String resolveText = "Đội ngoài xử lý hoàn tất sự cố. Đã lưu hóa đơn thực tế: "
                    + (saved.getActualCost() != null ? saved.getActualCost() + " VND" : "0 VND")
                    + ". Kíp xe tiếp tục hành trình chính.";
            appendBookingHistory(booking, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    resolveText);
            bookingRepository.save(booking);

            logAction("INCIDENT_RESOLVED", booking.getId(),
                    "Đã giải quyết sự cố INC-" + saved.getId() + ", hóa đơn: " + saved.getActualCost() + " VND");
        }

        log.info("Resolved Incident ID={}, actual cost={}", saved.getId(), saved.getActualCost());
        return mapToResponse(saved);
    }

    @Override
    public List<IncidentResponse> getIncidentsByBooking(Integer bookingId) {
        return incidentRepository.findByBookingID_Id(bookingId)
                .stream().map(this::mapToResponse).toList();
    }

    @Override
    public List<IncidentResponse> getAllIncidents(String status) {
        List<Incident> list = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL"))
                ? incidentRepository.findByStatus(status.toLowerCase())
                : incidentRepository.findAll();
        return list.stream().map(this::mapToResponse).toList();
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
        } catch (Exception ignored) {}
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
                .createdAt(inc.getCreatedAt() != null ? inc.getCreatedAt().toEpochMilli() : null)
                .resolvedAt(inc.getResolvedAt() != null ? inc.getResolvedAt().toEpochMilli() : null)
                .build();
    }
}
