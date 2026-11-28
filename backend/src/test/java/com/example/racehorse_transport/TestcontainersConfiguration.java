package com.example.racehorse_transport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// PostgreSQL thật trong Docker cho test. @ServiceConnection tự nối datasource của test vào container,
// nên test không phụ thuộc DB trên máy. Các test cùng cấu hình dùng chung một container (context được cache).
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
