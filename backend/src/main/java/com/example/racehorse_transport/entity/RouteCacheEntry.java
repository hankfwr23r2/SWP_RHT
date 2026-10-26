package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// Kết quả đã lấy từ dịch vụ chỉ đường (tuyến Valhalla, phí Vietmap), khóa là sha256 của bộ tham số
@Getter
@Setter
@Entity
@Table(name = "ROUTE_CACHE")
public class RouteCacheEntry {

    @Id
    @Column(name = "cache_key", length = 64, nullable = false)
    private String cacheKey;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
