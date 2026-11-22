package com.example.racehorse_transport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Dùng cho mọi test cần khởi động ứng dụng với DB thật:
// - PostgreSQL trong Docker (TestcontainersConfiguration);
// - profile "test" (src/test/resources/application-test.properties): Flyway nạp thêm dữ liệu mẫu db/seed-dev.
// Không đọc application-local.properties trên máy người chạy test.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public @interface PostgresIntegrationTest {
}
