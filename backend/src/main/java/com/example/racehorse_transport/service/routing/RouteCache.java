package com.example.racehorse_transport.service.routing;

import com.example.racehorse_transport.entity.RouteCacheEntry;
import com.example.racehorse_transport.repository.RouteCacheRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

// Lưu đệm kết quả dịch vụ chỉ đường 30 ngày: không gọi lại, và lộ trình với báo giá dùng cùng số liệu
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteCache {

    static final Duration TTL = Duration.ofDays(30);

    private final RouteCacheRepository repository;
    private final ObjectMapper json;

    public <T> Optional<T> find(String key, Class<T> type) {
        return repository.findById(hash(key))
                .filter(entry -> entry.getCreatedAt().isAfter(Instant.now().minus(TTL)))
                .flatMap(entry -> read(entry.getPayload(), type));
    }

    public void put(String key, Object value) {
        RouteCacheEntry entry = new RouteCacheEntry();
        entry.setCacheKey(hash(key));
        try {
            entry.setPayload(json.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không ghi được kết quả lộ trình vào bộ đệm", e);
        }
        entry.setCreatedAt(Instant.now());
        repository.save(entry);
    }

    static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> Optional<T> read(String payload, Class<T> type) {
        try {
            return Optional.of(json.readValue(payload, type));
        } catch (JsonProcessingException e) {
            log.warn("Bỏ qua bản lưu đệm lộ trình không đọc được: {}", e.getOriginalMessage());
            return Optional.empty();
        }
    }
}
