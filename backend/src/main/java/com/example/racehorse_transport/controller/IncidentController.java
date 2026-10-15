package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.incident.*;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    // 1. Driver/Escort Báo cáo sự cố khẩn cấp (SOS)
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/report")
    public ResponseEntity<ApiResponse<IncidentResponse>> reportIncident(@Valid @RequestBody ReportIncidentRequest request) {
        IncidentResponse response = incidentService.reportIncident(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã gửi báo động sự cố khẩn cấp!"));
    }

    // 2. Coordinator Đề xuất phương án Gọi Đội Ngoài (Cứu hộ xe / Thú y cơ động)
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    @PostMapping("/{id}/propose-external")
    public ResponseEntity<ApiResponse<IncidentResponse>> proposeExternalPlan(
            @PathVariable("id") Integer id,
            @RequestBody ProposeExternalPlanRequest request) {
        IncidentResponse response = incidentService.proposeExternalPlan(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã gửi đề xuất đội ngoài trình Quản lý"));
    }

    // 3. Manager Phê duyệt phương án & Bảo lãnh chi phí
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @PostMapping("/{id}/approve-plan")
    public ResponseEntity<ApiResponse<IncidentResponse>> approveIncidentPlan(
            @PathVariable("id") Integer id,
            @RequestBody ApproveIncidentPlanRequest request) {
        IncidentResponse response = incidentService.approveIncidentPlan(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Quản lý đã phê duyệt phương án giải quyết sự cố"));
    }

    // 4. Kíp xe Nghiệm thu hiện trường, Lưu hóa đơn đội ngoài & Tiếp tục hành trình
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/{id}/complete-resolution")
    public ResponseEntity<ApiResponse<IncidentResponse>> completeResolution(
            @PathVariable("id") Integer id,
            @RequestBody ResolveIncidentRequest request) {
        IncidentResponse response = incidentService.completeResolution(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã giải quyết sự cố và cập nhật hóa đơn đội ngoài thành công"));
    }

    // 5. Lấy danh sách sự cố
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'DRIVER', 'ESCORT', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<IncidentResponse>>> getAllIncidents(
            @RequestParam(value = "status", required = false) String status) {
        List<IncidentResponse> list = incidentService.getAllIncidents(status);
        return ResponseEntity.ok(ApiResponse.success(list, "Lấy danh sách sự cố thành công"));
    }

    // 6. Lấy sự cố theo đơn hàng
    @PreAuthorize("hasAnyRole('CUSTOMER', 'COORDINATOR', 'MANAGER', 'DRIVER', 'ESCORT', 'ADMIN')")
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<IncidentResponse>>> getIncidentsByBooking(
            @PathVariable("bookingId") Integer bookingId) {
        List<IncidentResponse> list = incidentService.getIncidentsByBooking(bookingId);
        return ResponseEntity.ok(ApiResponse.success(list, "Lấy danh sách sự cố theo đơn hàng thành công"));
    }
}
