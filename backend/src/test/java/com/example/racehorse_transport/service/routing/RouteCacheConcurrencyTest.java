package com.example.racehorse_transport.service.routing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// Hai màn hình xem trước cùng một tuyến cùng lúc: cả hai ghi bộ đệm cùng một khóa, không lần nào được lỗi.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RouteCacheConcurrencyTest {

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
    private RouteCache cache;

    @Test
    void concurrentWritesOfTheSameKeyNeverFail() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int round = 0; round < 20; round++) {
                String key = "concurrent|" + UUID.randomUUID();
                CyclicBarrier start = new CyclicBarrier(8);
                List<Future<?>> writes = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    long value = i;
                    writes.add(pool.submit(() -> {
                        start.await();
                        cache.put(key, value);
                        return null;
                    }));
                }
                for (Future<?> write : writes) {
                    write.get();
                }
                assertThat(cache.find(key, Long.class)).isPresent();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void writingAgainReplacesTheStoredValue() {
        String key = "replace|" + UUID.randomUUID();
        cache.put(key, 1L);
        cache.put(key, 2L);

        assertThat(cache.find(key, Long.class)).contains(2L);
    }
}
