package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.network.RouteRequestDto;
import com.example.racehorse_transport.dto.network.RouteResponseDto;
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
import org.springframework.web.bind.annotation.RestController;

// Tuyến trục chính cố định kèm điểm dừng.
@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {

    private static final String STAFF = "hasAnyRole('ADMIN', 'MANAGER', 'COORDINATOR', 'SPECIALIST', 'DRIVER', 'ESCORT')";

    private final RouteService routeService;

    @GetMapping
    @PreAuthorize(STAFF)
    public ResponseEntity<ApiResponseDto<List<RouteResponseDto>>> list() {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.routes(), "Danh sách tuyến"));
    }

    @GetMapping("/{id}")
    @PreAuthorize(STAFF)
    public ResponseEntity<ApiResponseDto<RouteResponseDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.route(id), "Chi tiết tuyến"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<RouteResponseDto>> create(@Valid @RequestBody RouteRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(routeService.createRoute(request), "Đã thêm tuyến"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<RouteResponseDto>> update(@PathVariable Long id, @Valid @RequestBody RouteRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.updateRoute(id, request), "Đã cập nhật tuyến"));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<RouteResponseDto>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.activateRoute(id), "Đã cho tuyến chạy lại"));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<RouteResponseDto>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(routeService.deactivateRoute(id), "Đã ngừng tuyến"));
    }
}
