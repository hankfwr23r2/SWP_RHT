package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.AuditLog;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.AuditLogRepository;
import com.example.racehorse_transport.service.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Ghi nhật ký kiểm toán trong cùng transaction với thao tác gốc: thao tác rollback thì nhật ký cũng không còn.
// Bên gọi không đưa mật khẩu, token, OTP hay số giấy tờ tùy thân vào oldData/newData (AGENTS.md mục 9).
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final int MAX_NAME_LENGTH = 50;

    private final AuditLogRepository auditLogs;
    private final AppUserRepository users;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void record(Long actorUserId, String action, String targetTable, Long targetId, Object oldData, Object newData) {
        requireName(action, "action");
        requireName(targetTable, "targetTable");
        if (targetId == null) {
            throw new IllegalArgumentException("Nhật ký kiểm toán phải có targetId.");
        }

        AuditLog log = new AuditLog();
        log.setActorUser(actorUserId == null ? null : users.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản " + actorUserId)));
        log.setAction(action);
        log.setTargetTable(targetTable);
        log.setTargetId(targetId);
        log.setOldData(toJson(oldData));
        log.setNewData(toJson(newData));
        auditLogs.save(log);
    }

    private static void requireName(String value, String field) {
        if (value == null || value.isBlank() || value.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Nhật ký kiểm toán: " + field + " bắt buộc và tối đa " + MAX_NAME_LENGTH + " ký tự.");
        }
    }

    private String toJson(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Không chuyển được dữ liệu kiểm toán sang JSON.", e);
        }
    }
}
