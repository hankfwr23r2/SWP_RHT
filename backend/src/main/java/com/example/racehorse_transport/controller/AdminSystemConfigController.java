package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.SystemConfigResponseDto;
import com.example.racehorse_transport.dto.admin.UpdateSystemConfigRequestDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/system-configs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<SystemConfigResponseDto>>> getAllConfigs() {
        return ResponseEntity.ok(ApiResponseDto.success(systemConfigService.getAllConfigs(),
                "Lấy danh sách tham số hệ thống thành công."));
    }

    @GetMapping("/grouped")
    public ResponseEntity<ApiResponseDto<Map<String, List<SystemConfigResponseDto>>>> getGroupedConfigs() {
        return ResponseEntity.ok(ApiResponseDto.success(systemConfigService.getAllConfigsGrouped(),
                "Lấy tham số hệ thống theo nhóm thành công."));
    }

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponseDto<SystemConfigResponseDto>> getConfig(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponseDto.success(systemConfigService.getConfig(key),
                "Lấy thông tin cấu hình thành công."));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponseDto<SystemConfigResponseDto>> updateConfig(
            @PathVariable String key,
            @Valid @RequestBody UpdateSystemConfigRequestDto request,
            Authentication authentication) {
        SystemConfigResponseDto updated = systemConfigService.updateConfig(key, request, authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(updated, "Cập nhật cấu hình thành công."));
    }
}
