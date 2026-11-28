package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.AuditLog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
