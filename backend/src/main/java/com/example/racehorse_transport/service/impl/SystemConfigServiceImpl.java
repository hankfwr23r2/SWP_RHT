package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.repository.SystemConfigRepository;
import com.example.racehorse_transport.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository repository;

    @Override
    @Transactional(readOnly = true)
    public int getInt(String key) {
        String value = repository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Thiếu tham số hệ thống " + key))
                .getValue();
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Tham số hệ thống " + key + " phải là số nguyên", e);
        }
    }
}
