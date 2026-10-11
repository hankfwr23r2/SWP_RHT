package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.TransactionLog;

import com.example.racehorse_transport.repository.TransactionLogRepository;
import com.example.racehorse_transport.service.TransactionLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TransactionLogServiceImpl implements TransactionLogService {

    private final TransactionLogRepository transactionLogRepository;

    @Autowired
    public TransactionLogServiceImpl(TransactionLogRepository transactionLogRepository) {
        this.transactionLogRepository = transactionLogRepository;
    }

    @Override
    public List<TransactionLog> findAll() {
        return transactionLogRepository.findAll();
    }

    @Override
    public Optional<TransactionLog> findById(Integer id) {
        return transactionLogRepository.findById(id);
    }

    @Override
    public TransactionLog save(TransactionLog entity) {
        return transactionLogRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        transactionLogRepository.deleteById(id);
    }
}
