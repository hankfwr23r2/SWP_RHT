package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseHealthLogDto;
import com.example.racehorse_transport.dto.UpdateHealthLogRequestDto;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.HorseHealthLogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/horse-health-logs")
public class HorseHealthLogController {

    private final HorseHealthLogService horseHealthLogService;
    private final ModelMapper modelMapper;
    private final BookingQueryService bookingQueries;

    @Autowired
    public HorseHealthLogController(HorseHealthLogService horseHealthLogService, ModelMapper modelMapper,
                                    BookingQueryService bookingQueries) {
        this.horseHealthLogService = horseHealthLogService;
        this.modelMapper = modelMapper;
        this.bookingQueries = bookingQueries;
    }

    // 1. Lấy tất cả nhật ký sức khỏe
    @PreAuthorize("hasAnyRole('SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR')")
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<HorseHealthLogDto>>> getAllLogs() {
        List<HorseHealthLog> logs = horseHealthLogService.findAll();
        List<HorseHealthLogDto> dtos = logs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponseDto.success(dtos, "Lấy danh sách nhật ký sức khỏe thành công"));
    }

    // 2. Lấy nhật ký sức khỏe theo mã ngựa
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR', 'ESCORT', 'DRIVER')")
    @GetMapping("/horse/{horseId}")
    public ResponseEntity<ApiResponseDto<List<HorseHealthLogDto>>> getLogsByHorse(@PathVariable Integer horseId) {
        List<HorseHealthLog> logs = horseHealthLogService.findByHorseId(horseId);
        List<HorseHealthLogDto> dtos = logs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponseDto.success(dtos, "Lấy nhật ký sức khỏe của ngựa thành công"));
    }

    // 3. Lấy nhật ký sức khỏe theo đơn hàng / chuyến đi
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR', 'ESCORT', 'DRIVER')")
    @GetMapping("/booking/{bookingCode}")
    public ResponseEntity<ApiResponseDto<List<HorseHealthLogDto>>> getLogsByBooking(@PathVariable String bookingCode,
                                                                                org.springframework.security.core.Authentication authentication) {
        boolean customer = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        List<HorseHealthLog> logs = horseHealthLogService.findByBookingCode(bookingCode, authentication.getName(), customer ? "CUSTOMER" : null);
        List<HorseHealthLogDto> dtos = logs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponseDto.success(dtos, "Lấy nhật ký sức khỏe chuyến đi thành công"));
    }

    // 4. Dev 4: Escort cập nhật sức khỏe / an sinh ngựa trên chuyến đi
    @PreAuthorize("hasAnyRole('ESCORT', 'DRIVER', 'SPECIALIST', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponseDto<HorseHealthLogDto>> createHealthLog(@Valid @RequestBody UpdateHealthLogRequestDto request) {
        HorseHealthLog saved = horseHealthLogService.updateHorseHealthStatusByEscort(
                request.getHorseId(),
                request.getBookingCode() == null ? null : bookingQueries.resolveBookingId(request.getBookingCode()),
                request.getHealthStatus(),
                request.getTemperature(),
                request.getWeight(),
                request.getSymptoms(),
                request.getEscortStaffId()
        );
        return ResponseEntity.ok(ApiResponseDto.success(toDTO(saved), "Cập nhật nhật ký sức khỏe ngựa thành công"));
    }

    private HorseHealthLogDto toDTO(HorseHealthLog entity) {
        HorseHealthLogDto dto = modelMapper.map(entity, HorseHealthLogDto.class);
        if (entity.getHorseID() != null) {
            dto.setHorseID(entity.getHorseID().getId());
        }
        if (entity.getBookingID() != null) {
            dto.setBookingCode(entity.getBookingID().getCode());
        }
        if (entity.getReportBy() != null) {
            dto.setReportBy(entity.getReportBy().getId());
        }
        return dto;
    }
}