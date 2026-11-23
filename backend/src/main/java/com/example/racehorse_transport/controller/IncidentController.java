package com.example.racehorse_transport.controller;


import com.example.racehorse_transport.dto.incident.ApproveIncidentPlanRequestDto;
import com.example.racehorse_transport.dto.incident.ConfirmIncidentFitRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentExpenseRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.dto.incident.ProposeExternalPlanRequestDto;
import com.example.racehorse_transport.dto.incident.RejectIncidentPlanRequestDto;
import com.example.racehorse_transport.dto.incident.ReportIncidentRequestDto;
import com.example.racehorse_transport.dto.incident.ResolveIncidentRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.IncidentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final BookingQueryService bookingQueries;

    // 1. Driver/Escort Báo cáo sự cố khẩn cấp (SOS)
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/report")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> reportIncident(
            Authentication authentication,
            @Valid @RequestBody ReportIncidentRequestDto request) {
        IncidentResponseDto response = incidentService.reportIncident(request, authentication.getName(), role(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response, "Đã gửi báo động sự cố khẩn cấp!"));
    }

    // 2. Coordinator Đề xuất phương án Gọi Đội Ngoài (Cứu hộ xe / Thú y cơ động)
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    @PostMapping("/{id}/propose-external")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> proposeExternalPlan(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody ProposeExternalPlanRequestDto request) {
        IncidentResponseDto response = incidentService.proposeExternalPlan(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã gửi đề xuất đội ngoài trình Quản lý"));
    }

    // 3. Manager Phê duyệt phương án & Bảo lãnh chi phí
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @PostMapping("/{id}/approve-plan")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> approveIncidentPlan(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody ApproveIncidentPlanRequestDto request) {
        IncidentResponseDto response = incidentService.approveIncidentPlan(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(response, "Quản lý đã phê duyệt phương án giải quyết sự cố"));
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @PostMapping("/{id}/reject-plan")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> rejectIncidentPlan(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody RejectIncidentPlanRequestDto request) {
        IncidentResponseDto response = incidentService.rejectIncidentPlan(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã trả phương án về Điều phối viên"));
    }

    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'ADMIN')")
    @PostMapping("/{id}/expenses")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> addExpense(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody IncidentExpenseRequestDto request) {
        IncidentResponseDto response = incidentService.addExpense(id, request, authentication.getName(), role(authentication));
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã ghi nhận chi phí sự cố"));
    }

    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT')")
    @DeleteMapping("/{id}/expenses/{expenseId}")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> deleteExpense(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @PathVariable("expenseId") String expenseId) {
        IncidentResponseDto response = incidentService.deleteExpense(id, expenseId, authentication.getName(), role(authentication));
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã xóa khoản chi phí sự cố"));
    }

    @PreAuthorize("hasAnyRole('ESCORT', 'ADMIN')")
    @PostMapping("/{id}/confirm-fit")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> confirmFit(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody(required = false) ConfirmIncidentFitRequestDto request) {
        IncidentResponseDto response = incidentService.confirmFit(id, request, authentication.getName(), role(authentication));
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã xác nhận ngựa đủ sức đi tiếp"));
    }

    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> resumeJourney(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        boolean confirmEarlyLeave = body != null && Boolean.TRUE.equals(body.get("confirmEarlyLeave"));
        String note = body != null && body.get("note") != null ? String.valueOf(body.get("note")) : null;
        IncidentResponseDto response = incidentService.resumeJourney(id, authentication.getName(), role(authentication), confirmEarlyLeave, note);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã tiếp tục hành trình"));
    }

    // 4. Kíp xe nghiệm thu hiện trường, lưu hóa đơn đội ngoài và tiếp tục hành trình
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/{id}/complete-resolution")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> completeResolution(
            Authentication authentication,
            @PathVariable("id") Integer id,
            @Valid @RequestBody ResolveIncidentRequestDto request) {
        IncidentResponseDto response = incidentService.completeResolution(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã giải quyết sự cố và cập nhật hóa đơn đội ngoài thành công"));
    }

    // 5. Lấy danh sách sự cố
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<IncidentResponseDto>>> getAllIncidents(
            @RequestParam(value = "status", required = false) String status) {
        List<IncidentResponseDto> list = incidentService.getAllIncidents(status);
        return ResponseEntity.ok(ApiResponseDto.success(list, "Lấy danh sách sự cố thành công"));
    }

    // 6. Lấy sự cố theo đơn hàng
    @PreAuthorize("hasAnyRole('CUSTOMER', 'COORDINATOR', 'MANAGER', 'DRIVER', 'ESCORT', 'ADMIN')")
    @GetMapping("/booking/{bookingCode}")
    public ResponseEntity<ApiResponseDto<List<IncidentResponseDto>>> getIncidentsByBooking(
            Authentication authentication,
            @PathVariable("bookingCode") String bookingCode) {
        List<IncidentResponseDto> list = incidentService.getIncidentsByBooking(bookingQueries.resolveBookingId(bookingCode), authentication.getName(), role(authentication));
        return ResponseEntity.ok(ApiResponseDto.success(list, "Lấy danh sách sự cố theo đơn hàng thành công"));
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
