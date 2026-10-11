package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.TransactionLog;

import java.util.List;
import java.util.Optional;

public interface TransactionLogService {
    List<TransactionLog> findAll();
    Optional<TransactionLog> findById(Integer id);
    TransactionLog save(TransactionLog entity);
    void deleteById(Integer id);
}
