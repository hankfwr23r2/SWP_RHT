package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Log;

import java.util.List;
import java.util.Optional;

public interface LogService {
    List<Log> findAll();
    Optional<Log> findById(Integer id);
    Log save(Log entity);
    void deleteById(Integer id);
}
