package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.health.HealthLogRequestDto;
import com.example.racehorse_transport.dto.health.HealthLogResponseDto;
import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.service.HealthLogService;
import com.example.racehorse_transport.service.HorseStayService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Sổ sức khỏe từng con và lưu trú ở hub (docs/MAN_HINH_VA_TAC_DONG.md mục 4.6, 4.8, 6.2)
@RestController
@RequiredArgsConstructor
public class HealthLogController {

    private final HealthLogService healthLogs;
    private final HorseStayService stays;

    @PostMapping("/health-logs")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT')")
    public ResponseEntity<ApiResponseDto<HealthLogResponseDto>> record(@Valid @RequestBody HealthLogRequestDto request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(healthLogs.record(request, authentication.getName()), "Đã ghi sức khỏe"));
    }

    @GetMapping("/booking-horses/{id}/health-logs")
    @PreAuthorize("hasAnyRole('DRIVER', 'ESCORT', 'COORDINATOR', 'MANAGER', 'SPECIALIST')")
    public ApiResponseDto<List<HealthLogResponseDto>> ofHorse(@PathVariable Long id) {
        return ApiResponseDto.success(healthLogs.ofHorse(id), "Sổ sức khỏe");
    }

    @GetMapping("/horse-stays")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<HorseStayResponseDto>> activeStays(@RequestParam Long locationId) {
        return ApiResponseDto.success(stays.activeAt(locationId), "Ngựa đang lưu trú");
    }
}
