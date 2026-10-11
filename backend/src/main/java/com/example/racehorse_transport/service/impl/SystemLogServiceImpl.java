package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.SystemLog;

import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.service.SystemLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SystemLogServiceImpl implements SystemLogService {

    private final SystemLogRepository systemLogRepository;

    @Autowired
    public SystemLogServiceImpl(SystemLogRepository systemLogRepository) {
        this.systemLogRepository = systemLogRepository;
    }

    @Override
    public List<SystemLog> findAll() {
        return systemLogRepository.findAll();
    }

    @Override
    public Optional<SystemLog> findById(Integer id) {
        return systemLogRepository.findById(id);
    }

    @Override
    public SystemLog save(SystemLog entity) {
        return systemLogRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        systemLogRepository.deleteById(id);
    }
}
