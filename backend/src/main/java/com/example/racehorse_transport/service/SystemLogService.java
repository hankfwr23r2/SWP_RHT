package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.audit.SystemLogPageResponseDto;
import com.example.racehorse_transport.dto.audit.SystemLogResponseDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// Xem nhật ký kiểm toán (audit_log) cho quản trị. Ghi nhật ký ở AuditService
public interface SystemLogService {

    SystemLogPageResponseDto search(Instant from, Instant to, String target, String action, String role, String actor, String key, int page, int size);

    SystemLogResponseDto get(Long id);

    // Bảng đích → các hành động đã có
    Map<String, List<String>> options();
}
