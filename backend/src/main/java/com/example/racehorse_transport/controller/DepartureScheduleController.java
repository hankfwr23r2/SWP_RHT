package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.network.DepartureScheduleRequestDto;
import com.example.racehorse_transport.dto.network.DepartureScheduleResponseDto;
import com.example.racehorse_transport.service.RouteService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Lịch chạy định kỳ của tuyến. Chuyến cụ thể theo ngày do DepartureService sinh từ lịch.
@RestController
@RequestMapping("/departure-schedules")
@RequiredArgsConstructor
public class DepartureScheduleController {

    private final RouteService routeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'COORDINATOR')")
    public ResponseEntity<ApiResponseDto<List<DepartureScheduleResponseDto>>> list(@RequestParam(required = false) Long routeId) {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.schedules(routeId), "Danh sách lịch chạy"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<DepartureScheduleResponseDto>> create(@Valid @RequestBody DepartureScheduleRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(routeService.createSchedule(request), "Đã thêm lịch chạy"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<DepartureScheduleResponseDto>> update(
            @PathVariable Long id, @Valid @RequestBody DepartureScheduleRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.updateSchedule(id, request), "Đã cập nhật lịch chạy"));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<DepartureScheduleResponseDto>> pause(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.pauseSchedule(id), "Đã tạm dừng lịch chạy"));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<DepartureScheduleResponseDto>> resume(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.resumeSchedule(id), "Đã chạy lại lịch"));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<DepartureScheduleResponseDto>> end(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.endSchedule(id), "Đã kết thúc lịch chạy"));
    }
}
