package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.network.HubServiceAreaRequestDto;
import com.example.racehorse_transport.dto.network.HubServiceAreaResponseDto;
import com.example.racehorse_transport.service.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Tỉnh nào do hub nào phục vụ. Mỗi tỉnh thuộc đúng một hub, nên tài nguyên được định danh bằng mã tỉnh.
@RestController
@RequestMapping("/hub-service-areas")
@RequiredArgsConstructor
public class HubServiceAreaController {

    private final LocationService locationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'COORDINATOR')")
    public ResponseEntity<ApiResponseDto<List<HubServiceAreaResponseDto>>> list() {
        return ResponseEntity.ok(ApiResponseDto.success(locationService.serviceAreas(), "Vùng phục vụ của các hub"));
    }

    @PutMapping("/{provinceCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<HubServiceAreaResponseDto>> assign(
            @PathVariable String provinceCode, @Valid @RequestBody HubServiceAreaRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(
                locationService.assignServiceArea(provinceCode, request.getHubLocationId()), "Đã giao tỉnh cho hub"));
    }

    @DeleteMapping("/{provinceCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<Void>> remove(@PathVariable String provinceCode) {
        locationService.removeServiceArea(provinceCode);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Đã bỏ tỉnh khỏi vùng phục vụ"));
    }
}
