package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.departure.DepartureVehicleRemoveRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureDetailResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureCancelRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureConfirmRunRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureCreateRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureDecisionResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureMergeRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionQueryDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionResponseDto;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.service.DepartureDecisionService;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.DepartureSuggestionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Chuyến cụ thể theo ngày: danh sách cho nhân viên, chuyến đột xuất, hủy chuyến, gợi ý chuyến cho khách.
@RestController
@RequestMapping("/departures")
@RequiredArgsConstructor
public class DepartureController {

    private final DepartureService departureService;
    private final DepartureSuggestionService suggestionService;
    private final DepartureDecisionService decisionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'COORDINATOR')")
    public ResponseEntity<ApiResponseDto<List<DepartureResponseDto>>> list(
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) DepartureStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponseDto.success(departureService.departures(routeId, status, from, to), "Danh sách chuyến"));
    }

    // Cách chạy và chuyến phù hợp cho đơn mới
    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'MANAGER', 'COORDINATOR')")
    public ResponseEntity<ApiResponseDto<DepartureSuggestionResponseDto>> suggestions(@Valid @ModelAttribute DepartureSuggestionQueryDto query) {
        return ResponseEntity.ok(ApiResponseDto.success(suggestionService.suggest(query), "Gợi ý chuyến"));
    }

    // Chuyến cần quyết định: số chỗ đã cọc, mốc quyết định, cảnh báo dưới mức tối thiểu
    @GetMapping("/decisions")
    @PreAuthorize("hasAnyRole('MANAGER', 'COORDINATOR')")
    public ResponseEntity<ApiResponseDto<List<DepartureDecisionResponseDto>>> decisions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponseDto.success(decisionService.decisions(from, to), "Chuyến cần quyết định"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'COORDINATOR')")
    public ResponseEntity<ApiResponseDto<DepartureDetailResponseDto>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(departureService.detail(id), "Chi tiết chuyến"));
    }

    @PostMapping("/{id}/vehicles/{allotmentId}/remove")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponseDto<DepartureDetailResponseDto>> removeVehicle(@PathVariable Long id,
            @PathVariable Long allotmentId, @Valid @RequestBody DepartureVehicleRemoveRequestDto request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDto.success(
                departureService.removeVehicle(id, allotmentId, request.reason(), authentication.getName()), "Đã bỏ xe"));
    }

    @PostMapping("/{id}/confirm-run")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponseDto<DepartureDecisionResponseDto>> confirmRun(@PathVariable Long id,
            @Valid @RequestBody(required = false) DepartureConfirmRunRequestDto request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDto.success(
                decisionService.confirmRun(id, authentication.getName(), request), "Đã xác nhận chạy"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponseDto<DepartureResponseDto>> createAdHoc(
            @Valid @RequestBody DepartureCreateRequestDto request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(departureService.createAdHoc(request, authentication.getName()), "Đã tạo chuyến đột xuất"));
    }

    // Người hủy lấy từ Authentication
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponseDto<DepartureResponseDto>> cancel(
            @PathVariable Long id, @Valid @RequestBody DepartureCancelRequestDto request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDto.success(
                departureService.cancel(id, request.getReason(), authentication.getName()), "Đã hủy chuyến"));
    }

    @GetMapping("/{id}/merge-targets")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponseDto<List<DepartureResponseDto>>> mergeTargets(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(departureService.mergeTargets(id), "Danh sách chuyến đích"));
    }

    @PostMapping("/{id}/merge")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponseDto<DepartureResponseDto>> merge(@PathVariable Long id,
            @Valid @RequestBody DepartureMergeRequestDto request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDto.success(
                departureService.merge(id, request, authentication.getName()), "Đã dồn chuyến"));
    }
}
