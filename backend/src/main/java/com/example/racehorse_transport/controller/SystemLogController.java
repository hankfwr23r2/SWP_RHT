package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.audit.SystemLogPageResponseDto;
import com.example.racehorse_transport.dto.audit.SystemLogResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.SystemLogService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Nhật ký hệ thống của quản trị (B6): đọc audit_log, giữ đúng hợp đồng trang /admin/system-logs
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemLogController {

    private final SystemLogService systemLogs;

    @GetMapping("/admin/system-logs")
    public ApiResponseDto<SystemLogPageResponseDto> search(
            @RequestParam Instant from, @RequestParam Instant to,
            @RequestParam(required = false) String target, @RequestParam(required = false) String action,
            @RequestParam(required = false) String role, @RequestParam(required = false) String actor,
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponseDto.success(systemLogs.search(from, to, target, action, role, actor, key, page, size), "Nhật ký hệ thống");
    }

    @GetMapping("/admin/system-logs/options")
    public ApiResponseDto<Map<String, List<String>>> options() {
        return ApiResponseDto.success(systemLogs.options(), "Bộ lọc nhật ký");
    }

    @GetMapping("/admin/system-logs/{id}")
    public ApiResponseDto<SystemLogResponseDto> get(@PathVariable Long id) {
        return ApiResponseDto.success(systemLogs.get(id), "Chi tiết nhật ký");
    }
}
