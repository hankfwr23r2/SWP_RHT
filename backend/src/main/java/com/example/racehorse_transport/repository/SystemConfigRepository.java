package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.SystemConfig;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
}
