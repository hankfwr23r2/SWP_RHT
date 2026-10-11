package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.SystemLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Integer> {
    List<SystemLog> findByTargetTableAndTargetRecordIDOrderByCreatedAtDesc(String targetTable, Integer targetRecordID);
    List<SystemLog> findByTargetTableOrderByCreatedAtDesc(String targetTable);
}
