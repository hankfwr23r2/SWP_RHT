package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthCheckController {

    // Commit đang chạy, để biết bản deploy đã cập nhật chưa mà không cần quyền vào Render
    private final String commit;

    public HealthCheckController(@Value("${app.commit}") String commit) {
        this.commit = commit;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> checkHealth() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "UP", "commit", commit), "Service is healthy"));
    }
}
