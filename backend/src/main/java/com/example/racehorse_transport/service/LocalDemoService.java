package com.example.racehorse_transport.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.io.Resource;

// Công cụ của chế độ chạy thử trên máy cá nhân
public interface LocalDemoService {
    Instant now();

    Duration offset();

    void advance(Duration amount);

    void reset();

    Resource file(String name);

    // Ký kết quả như cổng thật rồi báo về hệ thống; trả tham số cho trang kết quả của khách
    Map<String, String> pay(Map<String, String> request, boolean success);
}
