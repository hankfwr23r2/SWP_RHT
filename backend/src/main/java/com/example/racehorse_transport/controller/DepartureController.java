package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureCancelRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureCreateRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionQueryDto;
import com.example.racehorse_transport.dto.departure.DepartureSuggestionResponseDto;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
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

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponseDto<DepartureResponseDto>> createAdHoc(@Valid @RequestBody DepartureCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(departureService.createAdHoc(request), "Đã tạo chuyến đột xuất"));
    }

    // Người hủy lấy từ Authentication
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponseDto<DepartureResponseDto>> cancel(
            @PathVariable Long id, @Valid @RequestBody DepartureCancelRequestDto request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDto.success(
                departureService.cancel(id, request.getReason(), authentication.getName()), "Đã hủy chuyến"));
    }
}
