package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.incident.CostItemRequestDto;
import com.example.racehorse_transport.dto.incident.CostItemResponseDto;
import com.example.racehorse_transport.dto.incident.IncidentPlanRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentReportRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.dto.incident.PlanRejectRequestDto;
import com.example.racehorse_transport.dto.incident.VehicleSwapRequestDto;
import com.example.racehorse_transport.service.CostItemService;
import com.example.racehorse_transport.service.IncidentActionService;
import com.example.racehorse_transport.service.IncidentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Sự cố, phương án và chi phí (docs/MAN_HINH_VA_TAC_DONG.md mục 7.1–7.4, 6.7 phần chi phí)
@RestController
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidents;
    private final IncidentActionService actions;
    private final CostItemService costs;

    @GetMapping("/incidents")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<IncidentResponseDto>> open() {
        return ApiResponseDto.success(incidents.open(), "Sự cố đang xử lý");
    }

    @GetMapping("/vehicle-trips/{id}/incidents")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<IncidentResponseDto>> ofTrip(@PathVariable Long id) {
        return ApiResponseDto.success(incidents.ofTrip(id), "Sự cố của lượt xe");
    }

    @GetMapping("/incidents/{id}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'MANAGER')")
    public ApiResponseDto<IncidentResponseDto> incident(@PathVariable Long id) {
        return ApiResponseDto.success(incidents.incident(id), "Chi tiết sự cố");
    }

    @PostMapping("/incidents")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT')")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> report(@Valid @RequestBody IncidentReportRequestDto request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(incidents.report(request, authentication.getName()), "Đã báo sự cố"));
    }

    @PostMapping("/incidents/{id}/plans")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ResponseEntity<ApiResponseDto<IncidentResponseDto>> propose(@PathVariable Long id, @Valid @RequestBody IncidentPlanRequestDto request,
                                                                       Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(incidents.propose(id, request, authentication.getName()), "Đã lập phương án"));
    }

    @PostMapping("/incident-plans/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<IncidentResponseDto> approvePlan(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(incidents.approve(id, authentication.getName()), "Đã duyệt phương án");
    }

    @PostMapping("/incident-plans/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<IncidentResponseDto> rejectPlan(@PathVariable Long id, @Valid @RequestBody PlanRejectRequestDto request, Authentication authentication) {
        return ApiResponseDto.success(incidents.reject(id, request, authentication.getName()), "Đã từ chối phương án");
    }

    @PostMapping("/incidents/{id}/fit-confirmation")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR')")
    public ApiResponseDto<IncidentResponseDto> confirmFit(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(incidents.confirmFit(id, authentication.getName()), "Đã xác nhận ngựa đủ sức đi tiếp");
    }

    @PostMapping("/incidents/{id}/resolve")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<IncidentResponseDto> resolve(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(incidents.resolve(id, authentication.getName()), "Đã xử lý xong sự cố");
    }

    @PostMapping("/incidents/{id}/close")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<IncidentResponseDto> close(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(incidents.close(id, authentication.getName()), "Đã đóng sự cố");
    }

    // Thực hiện phương án đã duyệt: đổi xe, đưa ngựa về hub (B4)
    @PostMapping("/incidents/{id}/vehicle-swap")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<IncidentResponseDto> swapVehicle(@PathVariable Long id, @Valid @RequestBody VehicleSwapRequestDto request, Authentication authentication) {
        return ApiResponseDto.success(actions.swapVehicle(id, request.vehicleId(), authentication.getName()), "Đã đổi xe");
    }

    @PostMapping("/incidents/{id}/return-to-hub")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<IncidentResponseDto> returnToHub(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(actions.returnToHub(id, authentication.getName()), "Đã cho ngựa về hub");
    }

    @GetMapping("/incidents/{id}/cost-items")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<CostItemResponseDto>> costsOfIncident(@PathVariable Long id) {
        return ApiResponseDto.success(costs.ofIncident(id), "Chi phí của sự cố");
    }

    @GetMapping("/bookings/{id}/cost-items")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<CostItemResponseDto>> costsOfBooking(@PathVariable Long id) {
        return ApiResponseDto.success(costs.ofBooking(id), "Chi phí của đơn");
    }

    @PostMapping("/cost-items")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR')")
    public ResponseEntity<ApiResponseDto<CostItemResponseDto>> recordCost(@Valid @RequestBody CostItemRequestDto request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(costs.record(request, authentication.getName()), "Đã ghi chi phí"));
    }

    @PostMapping("/cost-items/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<CostItemResponseDto> approveCost(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(costs.approve(id, authentication.getName()), "Đã duyệt chi phí");
    }
}
