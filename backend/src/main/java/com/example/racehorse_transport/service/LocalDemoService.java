package com.example.racehorse_transport.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.core.io.Resource;

// Công cụ của chế độ chạy thử trên máy cá nhân
public interface LocalDemoService {
    Instant now();

    Duration offset();

    void advance(Duration amount);

    void reset();

    Resource file(String name);
}
