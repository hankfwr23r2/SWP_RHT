package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Log;

import com.example.racehorse_transport.repository.LogRepository;
import com.example.racehorse_transport.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LogServiceImpl implements LogService {

    private final LogRepository logRepository;

    @Autowired
    public LogServiceImpl(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Override
    public List<Log> findAll() {
        return logRepository.findAll();
    }

    @Override
    public Optional<Log> findById(Integer id) {
        return logRepository.findById(id);
    }

    @Override
    public Log save(Log entity) {
        return logRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        logRepository.deleteById(id);
    }
}
