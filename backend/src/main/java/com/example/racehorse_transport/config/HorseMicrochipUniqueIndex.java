package com.example.racehorse_transport.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Mỗi mã microchip chỉ thuộc một con ngựa. ddl-auto=update không thêm unique cho bảng đã có sẵn,
// nên tạo index ở đây (chạy lại nhiều lần vẫn an toàn). Dữ liệu cũ còn mã trùng thì chỉ ghi log, không chặn khởi động.
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class HorseMicrochipUniqueIndex implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_horse_microchipid ON horse (microchipid)");
        } catch (DataAccessException e) {
            log.error("Chưa tạo được unique index cho mã microchip, có thể dữ liệu đang có mã trùng: {}", e.getMessage());
        }
    }
}
