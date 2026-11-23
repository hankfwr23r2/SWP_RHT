package com.example.racehorse_transport.dto.audit;

import com.example.racehorse_transport.entity.AuditLog;
import java.time.Instant;

// Một dòng nhật ký hệ thống, giữ đúng các trường trang /admin/system-logs đang dùng.
// actorName là tên đăng nhập; targetLabel, description chưa có nguồn ở audit_log nên để trống; legacy luôn false
public record SystemLogResponseDto(
        Long id, String actionType, Long actorId, String actorName, String actorEmail, String actorRole,
        String targetTable, String targetKey, String targetLabel, String description, String oldData, String newData,
        boolean legacy, Instant createdAt) {

    public static SystemLogResponseDto from(AuditLog log) {
        var user = log.getActorUser();
        return new SystemLogResponseDto(log.getId(), log.getAction(), user == null ? null : user.getId(),
                user == null ? null : user.getUsername(), user == null ? null : user.getEmail(),
                user == null || user.getRole() == null ? null : user.getRole().getCode(),
                log.getTargetTable(), log.getTargetId() == null ? null : String.valueOf(log.getTargetId()), null, null,
                log.getOldData(), log.getNewData(), false, log.getCreatedAt());
    }
}
