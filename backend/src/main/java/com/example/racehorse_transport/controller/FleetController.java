package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.fleet.CrewResponse;
import com.example.racehorse_transport.dto.fleet.VehicleResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.FleetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/fleet")
@RequiredArgsConstructor
public class FleetController {

    private final FleetService fleetService;

    // 1. API Lấy danh sách xe (Có lọc khả dụng theo ngày khởi hành và loại chuyến nếu truyền tham số)
    @GetMapping("/vehicles")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getVehicles(
            @RequestParam(value = "departAt", required = false) Long departAtEpochMs,
            @RequestParam(value = "tripType", required = false, defaultValue = "domestic") String tripType) {

        List<VehicleResponse> vehicles;
        if (departAtEpochMs != null) {
            Instant departDate = Instant.ofEpochMilli(departAtEpochMs);
            vehicles = fleetService.getAvailableVehicles(departDate, tripType);
        } else {
            vehicles = fleetService.getAllVehicles();
        }

        return ResponseEntity.ok(ApiResponse.success(vehicles, "Lấy danh sách đội xe thành công"));
    }

    // 2. API Lấy danh sách nhân sự vận hành (Có lọc theo vai trò và kiểm tra trùng lịch theo ngày)
    @GetMapping("/crew")
    public ResponseEntity<ApiResponse<List<CrewResponse>>> getCrew(
            @RequestParam(value = "departAt", required = false) Long departAtEpochMs,
            @RequestParam(value = "role", required = false) String role) {

        List<CrewResponse> crew;
        if (departAtEpochMs != null) {
            Instant departDate = Instant.ofEpochMilli(departAtEpochMs);
            crew = fleetService.getAvailableCrew(departDate, role);
        } else {
            crew = fleetService.getAllCrew(role);
        }

        return ResponseEntity.ok(ApiResponse.success(crew, "Lấy danh sách nhân sự kíp xe thành công"));
    }
}
