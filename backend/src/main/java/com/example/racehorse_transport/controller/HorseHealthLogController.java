package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseHealthLogDTO;
import com.example.racehorse_transport.dto.UpdateHealthLogRequest;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.HorseHealthLogService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/horse-health-logs")
public class HorseHealthLogController {

    private final HorseHealthLogService horseHealthLogService;
    private final ModelMapper modelMapper;

    @Autowired
    public HorseHealthLogController(HorseHealthLogService horseHealthLogService, ModelMapper modelMapper) {
        this.horseHealthLogService = horseHealthLogService;
        this.modelMapper = modelMapper;
    }

    // 1. Lấy tất cả nhật ký sức khỏe
    @GetMapping
    public ResponseEntity<ApiResponse<List<HorseHealthLogDTO>>> getAllLogs() {
        List<HorseHealthLog> logs = horseHealthLogService.findAll();
        List<HorseHealthLogDTO> dtos = logs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy danh sách nhật ký sức khỏe thành công"));
    }

    // 2. Lấy nhật ký sức khỏe theo mã ngựa
    @GetMapping("/horse/{horseId}")
    public ResponseEntity<ApiResponse<List<HorseHealthLogDTO>>> getLogsByHorse(@PathVariable Integer horseId) {
        List<HorseHealthLog> logs = horseHealthLogService.findByHorseId(horseId);
        List<HorseHealthLogDTO> dtos = logs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy nhật ký sức khỏe của ngựa thành công"));
    }

    // 3. Lấy nhật ký sức khỏe theo đơn hàng / chuyến đi
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<HorseHealthLogDTO>>> getLogsByBooking(@PathVariable Integer bookingId) {
        List<HorseHealthLog> logs = horseHealthLogService.findByBookingId(bookingId);
        List<HorseHealthLogDTO> dtos = logs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy nhật ký sức khỏe chuyến đi thành công"));
    }

    // 4. Dev 4: Escort cập nhật sức khỏe / an sinh ngựa trên chuyến đi
    @PostMapping
    public ResponseEntity<ApiResponse<HorseHealthLogDTO>> createHealthLog(@Valid @RequestBody UpdateHealthLogRequest request) {
        HorseHealthLog saved = horseHealthLogService.updateHorseHealthStatusByEscort(
                request.getHorseId(),
                request.getBookingId(),
                request.getHealthStatus(),
                request.getTemperature(),
                request.getWeight(),
                request.getSymptoms(),
                request.getEscortStaffId()
        );
        return ResponseEntity.ok(ApiResponse.success(toDTO(saved), "Cập nhật nhật ký sức khỏe ngựa thành công"));
    }

    private HorseHealthLogDTO toDTO(HorseHealthLog entity) {
        HorseHealthLogDTO dto = modelMapper.map(entity, HorseHealthLogDTO.class);
        if (entity.getHorseID() != null) {
            dto.setHorseID(entity.getHorseID().getId());
        }
        if (entity.getBookingID() != null) {
            dto.setBookingID(entity.getBookingID().getId());
        }
        if (entity.getReportBy() != null) {
            dto.setReportBy(entity.getReportBy().getId());
        }
        return dto;
    }
}