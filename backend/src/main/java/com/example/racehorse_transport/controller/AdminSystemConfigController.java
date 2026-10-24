package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.SystemConfigResponse;
import com.example.racehorse_transport.dto.admin.UpdateSystemConfigRequest;
import com.example.racehorse_transport.response.ApiResponse;
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
    public ResponseEntity<ApiResponse<List<SystemConfigResponse>>> getAllConfigs() {
        return ResponseEntity.ok(ApiResponse.success(systemConfigService.getAllConfigs(),
                "Lấy danh sách tham số hệ thống thành công."));
    }

    @GetMapping("/grouped")
    public ResponseEntity<ApiResponse<Map<String, List<SystemConfigResponse>>>> getGroupedConfigs() {
        return ResponseEntity.ok(ApiResponse.success(systemConfigService.getAllConfigsGrouped(),
                "Lấy tham số hệ thống theo nhóm thành công."));
    }

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> getConfig(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.success(systemConfigService.getConfig(key),
                "Lấy thông tin cấu hình thành công."));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<SystemConfigResponse>> updateConfig(
            @PathVariable String key,
            @Valid @RequestBody UpdateSystemConfigRequest request,
            Authentication authentication) {
        SystemConfigResponse updated = systemConfigService.updateConfig(key, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật cấu hình thành công."));
    }
}
