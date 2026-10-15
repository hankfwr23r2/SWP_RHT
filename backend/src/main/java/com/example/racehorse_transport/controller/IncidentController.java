package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.incident.*;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<ApiResponse<IncidentResponse>> reportIncident(
            Authentication authentication,
            @Valid @RequestBody ReportIncidentRequest request) {
        IncidentResponse response = incidentService.reportIncident(request, authentication.getName(), role(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đã gửi báo động sự cố khẩn cấp!"));
    }

    // 2. Coordinator Đề xuất phương án Gọi Đội Ngoài (Cứu hộ xe / Thú y cơ động)
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    @PostMapping("/{id}/propose-external")
    public ResponseEntity<ApiResponse<IncidentResponse>> proposeExternalPlan(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody ProposeExternalPlanRequest request) {
        IncidentResponse response = incidentService.proposeExternalPlan(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response, "Đã gửi đề xuất đội ngoài trình Quản lý"));
    }

    // 3. Manager Phê duyệt phương án & Bảo lãnh chi phí
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @PostMapping("/{id}/approve-plan")
    public ResponseEntity<ApiResponse<IncidentResponse>> approveIncidentPlan(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody ApproveIncidentPlanRequest request) {
        IncidentResponse response = incidentService.approveIncidentPlan(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response, "Quản lý đã phê duyệt phương án giải quyết sự cố"));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @PostMapping("/{id}/reject-plan")
    public ResponseEntity<ApiResponse<IncidentResponse>> rejectIncidentPlan(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody RejectIncidentPlanRequest request) {
        IncidentResponse response = incidentService.rejectIncidentPlan(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response, "Đã trả phương án về Điều phối viên"));
    }

    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    @PostMapping("/{id}/expenses")
    public ResponseEntity<ApiResponse<IncidentResponse>> addExpense(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody IncidentExpenseRequest request) {
        IncidentResponse response = incidentService.addExpense(id, request, authentication.getName(), role(authentication));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã ghi nhận chi phí sự cố"));
    }

    @PreAuthorize("hasAnyRole('ESCORT', 'ADMIN')")
    @PostMapping("/{id}/confirm-fit")
    public ResponseEntity<ApiResponse<IncidentResponse>> confirmFit(
            Authentication authentication,
            @PathVariable("id") Integer id) {
        IncidentResponse response = incidentService.confirmFit(id, authentication.getName(), role(authentication));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã xác nhận ngựa đủ sức đi tiếp"));
    }

    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<IncidentResponse>> resumeJourney(
            Authentication authentication,
            @PathVariable("id") Integer id) {
        IncidentResponse response = incidentService.resumeJourney(id, authentication.getName(), role(authentication));
        return ResponseEntity.ok(ApiResponse.success(response, "Đã tiếp tục hành trình"));
    }

    // 4. Kíp xe nghiệm thu hiện trường, lưu hóa đơn đội ngoài và tiếp tục hành trình
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/{id}/complete-resolution")
    public ResponseEntity<ApiResponse<IncidentResponse>> completeResolution(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody ResolveIncidentRequest request) {
        IncidentResponse response = incidentService.completeResolution(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response, "Đã giải quyết sự cố và cập nhật hóa đơn đội ngoài thành công"));
    }

    // 5. Lấy danh sách sự cố
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
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
            Authentication authentication,
            @PathVariable("bookingId") Integer bookingId) {
        List<IncidentResponse> list = incidentService.getIncidentsByBooking(bookingId, authentication.getName(), role(authentication));
        return ResponseEntity.ok(ApiResponse.success(list, "Lấy danh sách sự cố theo đơn hàng thành công"));
    }

    private String role(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("");
    }
}
