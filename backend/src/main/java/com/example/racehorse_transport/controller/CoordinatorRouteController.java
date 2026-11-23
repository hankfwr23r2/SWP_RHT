package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.route.RoadRouteRequestDto;
import com.example.racehorse_transport.dto.route.RoadRouteResponseDto;
import com.example.racehorse_transport.dto.route.RoutePreviewRequestDto;
import com.example.racehorse_transport.dto.route.RoutePreviewResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.RoutePreviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/coordinator")
@PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class CoordinatorRouteController {

    private final RoutePreviewService routePreviewService;

    // Điều phối xem trước đường xe tải, lịch trình và phí cầu đường trước khi chốt xe
    @PostMapping("/route-preview")
    public ResponseEntity<ApiResponseDto<RoutePreviewResponseDto>> preview(@Valid @RequestBody RoutePreviewRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(routePreviewService.preview(request), "Đã tính lộ trình xe tải"));
    }

    // Đường xe tải cho phương án sự cố: tới trạm gần nhất, cứu hộ tới xe, đường tránh kẹt xe, phần đường còn lại
    @PostMapping("/road-route")
    public ResponseEntity<ApiResponseDto<RoadRouteResponseDto>> roadRoute(@Valid @RequestBody RoadRouteRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(routePreviewService.roadRoute(request), "Đã tính đường xe tải"));
    }
}
