package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.annotation.Transactional;

// Mốc trong lịch sử trạng thái đơn (hạn khiếu nại, tự đóng đơn) phải theo đồng hồ ứng dụng, kể cả khi tua giờ
@PostgresIntegrationTest
@Transactional
class BookingStatusHistoryClockTest {
    private static final Instant NOW = Instant.parse("2031-03-20T01:00:00Z");

    @TestBean(methodName = "fixedClock")
    private Clock clock;
    @Autowired private BookingStatusService statuses;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entities;

    static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Test
    void historyRowTakesTheApplicationClockTime() {
        var booking = data.booking(data.customer(data.unique("a3")), BookingType.INTERPROVINCE, BookingStatus.IN_OPERATION);

        statuses.transition(booking.getId(), BookingStatus.DELIVERED, null, null);
        entities.flush();

        assertThat(jdbc.queryForObject("""
                SELECT changed_at FROM booking_status_history WHERE booking_id = ? AND to_status = 'DELIVERED'
                """, Timestamp.class, booking.getId()).toInstant()).isEqualTo(NOW);
    }
}
