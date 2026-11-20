package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.fleet.CrewResponseDto;
import com.example.racehorse_transport.dto.fleet.VehicleResponseDto;
import com.example.racehorse_transport.dto.fleet.VehicleRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.FleetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/fleet")
@PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'SPECIALIST', 'ADMIN', 'DRIVER', 'ESCORT')")
@RequiredArgsConstructor
public class FleetController {

    private final FleetService fleetService;

    // 1. API Lấy danh sách xe (Có lọc khả dụng theo ngày khởi hành và loại chuyến nếu truyền tham số)
    @GetMapping("/vehicles")
    public ResponseEntity<ApiResponseDto<List<VehicleResponseDto>>> getVehicles(
            @RequestParam(value = "departAt", required = false) Long departAtEpochMs,
            @RequestParam(value = "tripType", required = false, defaultValue = "domestic") String tripType,
            @RequestParam(value = "bookingId", required = false) String bookingId) {

        List<VehicleResponseDto> vehicles;
        if (departAtEpochMs != null) {
            Instant departDate = Instant.ofEpochMilli(departAtEpochMs);
            vehicles = fleetService.getAvailableVehicles(departDate, tripType, bookingId);
        } else {
            vehicles = fleetService.getAllVehicles();
        }

        return ResponseEntity.ok(ApiResponseDto.success(vehicles, "Lấy danh sách đội xe thành công"));
    }

    // 2. API Lấy danh sách nhân sự vận hành (Có lọc theo vai trò và kiểm tra trùng lịch theo ngày)
    @GetMapping("/crew")
    public ResponseEntity<ApiResponseDto<List<CrewResponseDto>>> getCrew(
            @RequestParam(value = "departAt", required = false) Long departAtEpochMs,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "tripType", required = false, defaultValue = "domestic") String tripType,
            @RequestParam(value = "bookingId", required = false) String bookingId) {

        List<CrewResponseDto> crew;
        if (departAtEpochMs != null) {
            Instant departDate = Instant.ofEpochMilli(departAtEpochMs);
            crew = fleetService.getAvailableCrew(departDate, role, tripType, bookingId);
        } else {
            crew = fleetService.getAllCrew(role);
        }

        return ResponseEntity.ok(ApiResponseDto.success(crew, "Lấy danh sách nhân sự kíp xe thành công"));
    }

    @PostMapping("/vehicles")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponseDto<VehicleResponseDto>> createVehicle(@Valid @RequestBody VehicleRequestDto request) {
        return ResponseEntity.status(201).body(ApiResponseDto.success(fleetService.createVehicle(request), "Tạo xe thành công"));
    }

    @PutMapping("/vehicles/{id}")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponseDto<VehicleResponseDto>> updateVehicle(@PathVariable String id, @Valid @RequestBody VehicleRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(fleetService.updateVehicle(id, request), "Cập nhật xe thành công"));
    }

    @DeleteMapping("/vehicles/{id}")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponseDto<Void>> deleteVehicle(@PathVariable String id) {
        fleetService.deleteVehicle(id);
        return ResponseEntity.ok(ApiResponseDto.success(null, "Xóa xe thành công"));
    }
}
