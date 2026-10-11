package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.TransactionLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Integer> {
}
