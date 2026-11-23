package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.network.LocationRequestDto;
import com.example.racehorse_transport.dto.network.LocationResponseDto;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.service.LocationService;
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

// Điểm thuộc mạng lưới: hub, trại cách ly, trạm nghỉ, cửa khẩu, chuồng, trường đua.
@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDto<List<LocationResponseDto>>> list(
            @RequestParam(required = false) LocationType type,
            @RequestParam(required = false) ActiveStatus status) {
        return ResponseEntity.ok(ApiResponseDto.success(locationService.locations(type, status), "Danh sách địa điểm"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponseDto<LocationResponseDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(locationService.location(id), "Chi tiết địa điểm"));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<LocationResponseDto>> create(@Valid @RequestBody LocationRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(locationService.createLocation(request), "Đã thêm địa điểm"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<LocationResponseDto>> update(@PathVariable Long id, @Valid @RequestBody LocationRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(locationService.updateLocation(id, request), "Đã cập nhật địa điểm"));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<LocationResponseDto>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(locationService.activateLocation(id), "Đã dùng lại địa điểm"));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<LocationResponseDto>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseDto.success(locationService.deactivateLocation(id), "Đã ngừng dùng địa điểm"));
    }
}
