package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.AuditLog;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.AuditLogRepository;
import com.example.racehorse_transport.service.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository logs;
    private final AppUserRepository accounts;
    private final ObjectMapper json;

    @Override
    @Transactional
    public void record(Long actorUserId, String action, String targetTable, Long targetId, Object oldData, Object newData) {
        AuditLog log = new AuditLog();
        if (actorUserId != null) {
            log.setActorUser(accounts.getReferenceById(actorUserId));
        }
        log.setAction(action);
        log.setTargetTable(targetTable);
        log.setTargetId(targetId);
        log.setOldData(snapshot(oldData));
        log.setNewData(snapshot(newData));
        logs.save(log);
    }

    private String snapshot(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return json.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException("Không tạo được bản chụp kiểm toán.", ex);
        }
    }
}
