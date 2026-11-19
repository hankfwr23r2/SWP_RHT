package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.entity.RouteCacheEntry;
import com.example.racehorse_transport.repository.RouteCacheRepository;
import com.example.racehorse_transport.service.RouteCacheService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

// Tuyến và phí đã lấy được lưu 30 ngày để không gọi lại dịch vụ ngoài và để lộ trình, báo giá dùng cùng số liệu.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RouteCacheServiceImplTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RouteCacheService cache;
    @Autowired
    private RouteCacheRepository repository;

    private static String newKey() {
        return "test|" + UUID.randomUUID();
    }

    private static RoadRouteDto sampleRoute() {
        return new RoadRouteDto(238.729, 20501, List.of(
                new RoadRouteDto.Leg(74.761, 8029, List.of(new GeoPointDto(10.7757, 106.7013), new GeoPointDto(11.0796, 106.1572))),
                new RoadRouteDto.Leg(163.967, 12472, List.of(new GeoPointDto(11.0796, 106.1572), new GeoPointDto(11.5564, 104.9282)))));
    }

    @Test
    void storedRouteComesBackUnchanged() {
        String key = newKey();
        cache.put(key, sampleRoute());

        assertThat(cache.find(key, RoadRouteDto.class)).contains(sampleRoute());
    }

    @Test
    void storedTollComesBackUnchanged() {
        String key = newKey();
        cache.put(key, 3_111_000L);

        assertThat(cache.find(key, Long.class)).contains(3_111_000L);
    }

    @Test
    void unknownKeyIsAMiss() {
        assertThat(cache.find(newKey(), RoadRouteDto.class)).isEmpty();
    }

    @Test
    void entriesOlderThanThirtyDaysAreIgnored() {
        String key = newKey();
        cache.put(key, sampleRoute());
        RouteCacheEntry entry = repository.findById(RouteCacheServiceImpl.hash(key)).orElseThrow();
        entry.setCreatedAt(Instant.now().minus(Duration.ofDays(31)));
        repository.save(entry);

        assertThat(cache.find(key, RoadRouteDto.class)).isEmpty();
    }

    @Test
    void unreadableEntryIsTreatedAsAMiss() {
        String key = newKey();
        RouteCacheEntry entry = new RouteCacheEntry();
        entry.setCacheKey(RouteCacheServiceImpl.hash(key));
        entry.setPayload("{không phải json");
        entry.setCreatedAt(Instant.now());
        repository.save(entry);

        assertThat(cache.find(key, RoadRouteDto.class)).isEmpty();
    }

    @Test
    void sameKeyAlwaysHashesTheSameWay() {
        assertThat(RouteCacheServiceImpl.hash("valhalla|a")).isEqualTo(RouteCacheServiceImpl.hash("valhalla|a")).hasSize(64);
        assertThat(RouteCacheServiceImpl.hash("valhalla|a")).isNotEqualTo(RouteCacheServiceImpl.hash("valhalla|b"));
    }
}
