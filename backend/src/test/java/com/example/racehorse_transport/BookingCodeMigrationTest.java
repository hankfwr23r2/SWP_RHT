package com.example.racehorse_transport;

import com.example.racehorse_transport.config.BookingCodeMigration;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

// Mã đơn là chuỗi ngẫu nhiên lưu riêng, không ghép từ BookingID tự tăng.
// Đơn có từ trước (chưa có mã, vận đơn VD-2026-0007, thông báo trỏ BK-<số>) được gán lại khi backend khởi động.
@SpringBootTest
@MockTurnstileVerification
class BookingCodeMigrationTest {

    private static final String CODE = "^BK-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$";
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
    private BookingRepository bookings;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BookingCodeMigration migration;

    @Test
    void newBookingGetsRandomCodeOnSave() {
        Booking booking = bookings.save(Booking.builder().status("PENDING").build());

        assertThat(booking.getCode()).matches(CODE);
    }

    @Test
    void legacyBookingWaybillAndNotificationsAreRewrittenOnceAndStayStable() {
        Booking legacy = bookings.save(Booking.builder().status("DEPOSIT_PAID").build());
        Booking current = bookings.save(Booking.builder().status("PENDING").build());
        String currentCode = current.getCode();
        int id = legacy.getId();
        jdbc.update("update booking set booking_code = null, waybill_no = 'VD-2026-' || lpad(?::text, 4, '0') where bookingid = ?", id, id);
        Notification customerNotice = notifications.save(Notification.builder()
                .title("Đơn BK-" + id).bookingId("BK-" + id)
                .link("/orders/BK-" + String.format("%03d", id)).isRead(false).build());
        Notification staffNotice = notifications.save(Notification.builder()
                .title("Thẩm định").bookingId("BK-" + id)
                .link("/specialist/verification/BK-" + id).isRead(false).build());

        migration.run();

        Booking migrated = bookings.findById(id).orElseThrow();
        assertThat(migrated.getCode()).matches(CODE);
        assertThat(migrated.getWaybillNo()).isEqualTo("VD-2026-" + migrated.getCode().substring(3));
        assertThat(bookings.findById(current.getId()).orElseThrow().getCode()).isEqualTo(currentCode);
        Notification customerAfter = notifications.findById(customerNotice.getId()).orElseThrow();
        assertThat(customerAfter.getBookingId()).isEqualTo(migrated.getCode());
        assertThat(customerAfter.getLink()).isEqualTo("/orders/" + migrated.getCode());
        assertThat(notifications.findById(staffNotice.getId()).orElseThrow().getLink())
                .isEqualTo("/specialist/verification/" + migrated.getCode());

        migration.run();

        Booking again = bookings.findById(id).orElseThrow();
        assertThat(again.getCode()).isEqualTo(migrated.getCode());
        assertThat(again.getWaybillNo()).isEqualTo(migrated.getWaybillNo());
    }
}
