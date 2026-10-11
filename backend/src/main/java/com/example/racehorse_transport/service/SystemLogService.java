package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.SystemLog;

import java.util.List;
import java.util.Optional;

public interface SystemLogService {
    List<SystemLog> findAll();
    Optional<SystemLog> findById(Integer id);
    SystemLog save(SystemLog entity);
    void deleteById(Integer id);
}
