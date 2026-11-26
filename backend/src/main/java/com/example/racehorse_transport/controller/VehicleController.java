package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleClassResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleCreateRequestDto;
import com.example.racehorse_transport.dto.vehicle.VehicleResponseDto;
import com.example.racehorse_transport.dto.vehicle.VehicleUpdateRequestDto;
import com.example.racehorse_transport.service.VehicleService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping("/vehicles")
    public ApiResponseDto<List<VehicleResponseDto>> listVehicles() {
        return ApiResponseDto.success(vehicleService.vehicles(), "Danh sách xe");
    }

    @GetMapping("/vehicles/{id}")
    public ApiResponseDto<VehicleResponseDto> getVehicle(@PathVariable Long id) {
        return ApiResponseDto.success(vehicleService.vehicle(id), "Chi tiết xe");
    }

    @GetMapping("/vehicle-classes")
    public ApiResponseDto<List<VehicleClassResponseDto>> vehicleClasses() {
        return ApiResponseDto.success(vehicleService.vehicleClasses(), "Danh sách hạng xe");
    }

    @PostMapping("/vehicles")
    public ResponseEntity<ApiResponseDto<VehicleResponseDto>> createVehicle(@Valid @RequestBody VehicleCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(vehicleService.createVehicle(request), "Đã thêm xe"));
    }

    @PutMapping("/vehicles/{id}")
    public ApiResponseDto<VehicleResponseDto> updateVehicle(@PathVariable Long id, @Valid @RequestBody VehicleUpdateRequestDto request) {
        return ApiResponseDto.success(vehicleService.updateVehicle(id, request), "Đã cập nhật xe");
    }
}
