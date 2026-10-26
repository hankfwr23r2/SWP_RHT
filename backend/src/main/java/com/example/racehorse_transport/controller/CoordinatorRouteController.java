package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.routing.RoutePreviewRequest;
import com.example.racehorse_transport.dto.routing.RoutePreviewResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.routing.RoutePreviewService;
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
    public ResponseEntity<ApiResponse<RoutePreviewResponse>> preview(@Valid @RequestBody RoutePreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(routePreviewService.preview(request), "Đã tính lộ trình xe tải"));
    }
}
