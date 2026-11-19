package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.SystemLogResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import static com.example.racehorse_transport.service.AdminAuditService.ACTIONS;
import com.example.racehorse_transport.service.AdminSystemLogService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/system-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSystemLogController {
    private final AdminSystemLogService service;

    @GetMapping
    public ApiResponseDto<AdminSystemLogService.LogPage> search(
            @RequestParam(required = false) String target, @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor, @RequestParam(required = false) String key,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String role) {
        return ApiResponseDto.success(service.search(target, action, actor, key, from, to, page, size, role), "Lấy nhật ký quản trị thành công");
    }

    @GetMapping("/options")
    public ApiResponseDto<java.util.Map<String, java.util.List<String>>> options() {
        return ApiResponseDto.success(ACTIONS, "Danh mục nhật ký quản trị");
    }

    @GetMapping("/{id}")
    public ApiResponseDto<SystemLogResponseDto> detail(@PathVariable Integer id) {
        return ApiResponseDto.success(service.detail(id), "Lấy chi tiết nhật ký thành công");
    }
}
