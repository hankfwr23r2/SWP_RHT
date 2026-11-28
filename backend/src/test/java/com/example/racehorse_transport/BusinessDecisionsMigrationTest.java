package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class BusinessDecisionsMigrationTest {

    @Test
    void upgradesExistingGatesAndAddsBusinessReferenceData() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("202611231800").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("""
                    INSERT INTO location (code, name, location_type, country_code, address, status)
                    VALUES ('GATE-MOCBAI', 'Moc Bai', 'BORDER_GATE', 'VN', 'Moc Bai', 'ACTIVE'),
                           ('GATE-OTHER', 'Other gate', 'BORDER_GATE', 'VN', 'Other', 'ACTIVE'),
                           ('TEST-HUB', 'Hub', 'HUB', 'VN', 'Hub', 'ACTIVE')
                    """);
            insertBookingsHoldingWideStalls(jdbc);

            Flyway.configure().dataSource(source).target("202611231900").load().migrate();

            assertThat(jdbc.queryForList("""
                    SELECT table_name || '.' || column_name FROM information_schema.columns
                    WHERE table_schema = 'public' AND column_name IN
                    ('quarantine_days', 'registration_expiry_date', 'counterpart_country_code', 'pickup_method', 'delivery_method')
                    """, String.class)).containsExactlyInAnyOrder("location.counterpart_country_code",
                    "booking.pickup_method", "booking.delivery_method");
            assertThat(jdbc.queryForList("SELECT pickup_method || delivery_method FROM booking", String.class))
                    .hasSize(2).containsOnly("COMPANYCOMPANY");
            assertThatThrownBy(() -> jdbc.update("UPDATE booking SET pickup_method = 'OTHER'"))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_booking_pickup_method");
            // 2 ngựa khoang rộng và 1 khoang thường là 5 chỗ; đơn bị từ chối và ngựa bị loại không tính
            assertThat(jdbc.queryForObject("SELECT booked_stall_count FROM departure", Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT counterpart_country_code FROM location WHERE code = 'GATE-MOCBAI'",
                    String.class)).isEqualTo("KH");
            assertThat(jdbc.queryForObject("SELECT counterpart_country_code FROM location WHERE code = 'GATE-OTHER'",
                    String.class)).isNull();
            assertThatThrownBy(() -> jdbc.update("UPDATE location SET counterpart_country_code = 'KH' WHERE code = 'TEST-HUB'"))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_location_counterpart_country");
            assertThatThrownBy(() -> jdbc.update("UPDATE location SET counterpart_country_code = 'ZZ' WHERE code = 'GATE-OTHER'"))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("foreign key");

            assertThat(jdbc.queryForList("""
                    SELECT code FROM document_type WHERE owner_type = 'BOOKING_HORSE' AND code IN
                    ('QUARANTINE_CLEARANCE', 'ORIGIN_QUARANTINE_CERT', 'IMPORT_APPROVAL', 'IMPORT_TRANSPORT_CERT')
                    """, String.class)).containsExactlyInAnyOrder("QUARANTINE_CLEARANCE", "ORIGIN_QUARANTINE_CERT",
                    "IMPORT_APPROVAL", "IMPORT_TRANSPORT_CERT");
            assertThat(jdbc.queryForList("""
                    SELECT name FROM billable_service WHERE code IN ('VET_CHECK', 'QUARANTINE_STAY')
                    """, String.class)).containsExactlyInAnyOrder("Thú y tại chuồng: khám và lấy mẫu", "Lưu chuồng theo ngày");
            assertThat(jdbc.queryForList("""
                    SELECT config_key || ':' || config_value || ':' || unit FROM system_config
                    WHERE config_key IN ('DEPARTURE_DECISION_HOURS', 'DEPARTURE_MIN_FILL_PERCENT', 'HUB_CHECKIN_HOURS',
                    'BALANCE_WAIT_MINUTES', 'MAX_CONTINUOUS_HOURS', 'MIN_REST_MINUTES', 'MAX_DRIVE_HOURS_PER_DAY',
                    'OVERNIGHT_REST_HOURS', 'DOCUMENT_REMINDER_DAYS', 'PRICE_HOLD_DAYS', 'SETTLEMENT_AUTO_CLOSE_DAYS',
                    'BALANCE_DUE_DAYS_BEFORE_PICKUP')
                    """, String.class)).containsExactlyInAnyOrder("DEPARTURE_DECISION_HOURS:48:HOUR",
                    "DEPARTURE_MIN_FILL_PERCENT:30:COUNT", "HUB_CHECKIN_HOURS:3:HOUR", "BALANCE_WAIT_MINUTES:30:MINUTE",
                    "MAX_CONTINUOUS_HOURS:4:HOUR", "MIN_REST_MINUTES:30:MINUTE", "MAX_DRIVE_HOURS_PER_DAY:10:HOUR",
                    "OVERNIGHT_REST_HOURS:10:HOUR", "DOCUMENT_REMINDER_DAYS:5:DAY", "PRICE_HOLD_DAYS:30:DAY",
                    "SETTLEMENT_AUTO_CLOSE_DAYS:7:DAY");
        }
    }

    private static void insertBookingsHoldingWideStalls(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO app_user (username, email, password_hash, is_email_verified, preferred_language, status, role_code)"
                + " VALUES ('wide.test', 'wide@racehorse.test', 'x', true, 'vi', 'ACTIVE', 'CUSTOMER')");
        jdbc.update("INSERT INTO customer (user_id, customer_type, full_name, phone, address, country_code)"
                + " SELECT id, 'PERSON', 'Khách', '0900000000', 'Địa chỉ', 'VN' FROM app_user WHERE username = 'wide.test'");
        jdbc.update("INSERT INTO route (code, name, origin_location_id, destination_location_id, distance_km, estimated_duration_minutes, status)"
                + " SELECT 'WIDE-TEST', 'Tuyến', o.id, d.id, 10, 60, 'ACTIVE' FROM location o, location d"
                + " WHERE o.code = 'TEST-HUB' AND d.code = 'GATE-MOCBAI'");
        jdbc.update("INSERT INTO departure (route_id, departure_date, planned_departure_at, booking_cutoff_at, stall_capacity, booked_stall_count, status)"
                + " SELECT id, current_date + 10, now() + interval '10 day', now() + interval '7 day', 9, 3, 'OPEN' FROM route WHERE code = 'WIDE-TEST'");
        for (int i = 1; i <= 5; i++) {
            jdbc.update("INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status, updated_at)"
                    + " SELECT c.user_id, ?, 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE', now() FROM customer c", "Ngựa " + i);
        }
        for (String[] row : new String[][] {{"W-1", "UNDER_REVIEW"}, {"W-2", "REJECTED"}}) {
            jdbc.update("INSERT INTO booking (code, customer_id, booking_type, departure_id, requested_pickup_date, status, created_by)"
                    + " SELECT ?, c.user_id, 'EXPORT', d.id, current_date + 9, ?, c.user_id FROM customer c, departure d", row[0], row[1]);
        }
        addHorse(jdbc, "W-1", "Ngựa 1", "WIDE", "ACTIVE");
        addHorse(jdbc, "W-1", "Ngựa 2", "WIDE", "ACTIVE");
        addHorse(jdbc, "W-1", "Ngựa 3", "STANDARD", "ACTIVE");
        addHorse(jdbc, "W-1", "Ngựa 4", "WIDE", "EXCLUDED");
        addHorse(jdbc, "W-2", "Ngựa 5", "WIDE", "ACTIVE");
    }

    private static void addHorse(JdbcTemplate jdbc, String booking, String horse, String stall, String status) {
        jdbc.update("INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status)"
                + " SELECT b.id, h.id, ?, ? FROM booking b, horse h WHERE b.code = ? AND h.name = ?", stall, status, booking, horse);
    }
}
