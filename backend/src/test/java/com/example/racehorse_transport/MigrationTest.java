package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Flyway dựng đủ 59 bảng theo docs/DB_SCHEMA_59_BANG.md, và các ràng buộc quan trọng thật sự chặn dữ liệu sai.
// Mỗi test chạy trong một transaction và được rollback. Câu lệnh vi phạm ràng buộc luôn là câu cuối của test.
@PostgresIntegrationTest
@Transactional
class MigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long id(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        assertThat(value).isNotNull();
        return value;
    }

    private long customerPartyId() {
        return id("SELECT p.id FROM party p JOIN app_user u ON u.id = p.user_id WHERE u.username = 'customer1.dev'");
    }

    private long userId(String username) {
        return id("SELECT id FROM app_user WHERE username = ?", username);
    }

    private long newBooking() {
        return id("""
                INSERT INTO booking (code, customer_party_id, booking_type, requested_pickup_date, status, created_by)
                VALUES ('TEST-001', ?, 'EXPORT', DATE '2026-12-13', 'QUOTING', ?)
                RETURNING id""", customerPartyId(), userId("customer1.dev"));
    }

    private long newQuotation(long bookingId) {
        return id("""
                INSERT INTO quotation (booking_id, version, price_list_id, currency, deposit_rate, valid_until,
                                       confirmed_pickup_date, confirmed_departure_date, estimated_delivery_date,
                                       total_amount, pass_through_amount, deposit_amount, status)
                VALUES (?, 1, (SELECT id FROM price_list WHERE name = 'Bảng giá mẫu (dev)'), 'VND', 0.3, now() + interval '1 day',
                        DATE '2026-12-13', DATE '2026-12-20', DATE '2026-12-25', 10000000, 1000000, 3700000, 'DRAFT')
                RETURNING id""", bookingId);
    }

    private long newBookingHorse(long bookingId) {
        long horseId = id("""
                INSERT INTO horse (owner_party_id, name, microchip, gender, horse_type_id, status)
                VALUES (?, 'Sao Mai', '985000000000001', 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE')
                RETURNING id""", customerPartyId());
        return id("""
                INSERT INTO booking_horse (booking_id, horse_id, stall_type, status)
                VALUES (?, ?, 'STANDARD', 'ACTIVE')
                RETURNING id""", bookingId, horseId);
    }

    @Test
    void createsAllFiftyNineTables() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'""",
                Integer.class);
        assertThat(tables).isEqualTo(59);
    }

    @Test
    void loadsReferenceAndDevSeedData() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM province WHERE country_code = 'VN'", Integer.class)).isEqualTo(34);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM province", Integer.class)).isEqualTo(77);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT name FROM role WHERE code = 'ESCORT'", String.class)).isEqualTo("Nhân viên hộ tống");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE username LIKE '%.dev'", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM route_stop", Integer.class)).isEqualTo(13);
    }

    @Test
    void devSeedHasConfirmedBookingsForOperations() {
        // Ba đơn CONFIRMED để luồng vận hành điều độ: một chạy thẳng, một trục nội địa, một quốc tế
        assertThat(jdbc.queryForList("""
                SELECT b.code FROM booking b
                JOIN quotation q ON q.booking_id = b.id AND q.status = 'ACCEPTED'
                WHERE b.code LIKE 'DEV-%' AND b.status = 'CONFIRMED'
                ORDER BY b.code""", String.class))
                .containsExactly("DEV-DIRECT-001", "DEV-DOMESTIC-001", "DEV-EXPORT-001");
        assertThat(jdbc.queryForObject("SELECT departure_id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class)).isNull();
        // Tổng báo giá khớp các dòng, và đã trả đủ tổng
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM quotation q
                JOIN booking b ON b.id = q.booking_id AND b.code LIKE 'DEV-%'
                WHERE q.total_amount <> (SELECT sum(amount) FROM quotation_line l WHERE l.quotation_id = q.id)
                   OR q.total_amount <> (SELECT sum(amount) FROM payment p WHERE p.quotation_id = q.id AND p.status = 'PAID')""",
                Integer.class)).isZero();
        // Khoang đã đặt của chuyến khớp số ngựa của các đơn mẫu đi trục (2 + 1)
        assertThat(jdbc.queryForObject("""
                SELECT sum(d.booked_stalls) FROM departure d WHERE d.id IN (SELECT departure_id FROM booking WHERE code LIKE 'DEV-%')""",
                Integer.class)).isEqualTo(3);
    }

    @Test
    void departureCannotBeOverbooked() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO departure (route_id, departure_date, planned_depart_at, booking_cutoff_at, capacity_stalls, booked_stalls, status)
                VALUES ((SELECT id FROM route WHERE code = 'HCM-PNH'), DATE '2026-12-21',
                        TIMESTAMPTZ '2026-12-21 06:00+07', TIMESTAMPTZ '2026-12-18 06:00+07', 9, 10, 'OPEN')"""))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_departure_booked_stalls");
    }

    @Test
    void activePriceListsOfSameCurrencyCannotOverlap() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO price_list (name, currency, margin_rate, fuel_buffer_rate, deposit_rate, valid_from, status)
                VALUES ('Bảng giá trùng', 'VND', 0.05, 0.05, 0.3, DATE '2027-01-01', 'ACTIVE')"""))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ex_price_list_active_period");
    }

    @Test
    void countryRulesOfSameDirectionCannotOverlap() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO country_rule (country_code, direction, permit_lead_days, cert_lead_days, quarantine_days, valid_from)
                VALUES ('KH', 'EXPORT', 10, 5, 5, DATE '2027-01-01')"""))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ex_country_rule_period");
    }

    @Test
    void provinceBelongsToExactlyOneHub() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO hub_service_area (hub_location_id, province_code)
                VALUES ((SELECT id FROM location WHERE code = 'HUB-DN'), 'VN-79')"""))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_hub_service_area_province_code");
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_user (username, email, password_hash, status)
                VALUES ('another.admin', 'ADMIN.DEV@racehorse.test', 'x', 'ACTIVE')"""))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("uk_app_user_email");
    }

    @Test
    void depositMustCoverPassThroughAndNotExceedTotal() {
        long bookingId = newBooking();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO quotation (booking_id, version, price_list_id, currency, deposit_rate, valid_until,
                                       confirmed_pickup_date, confirmed_departure_date, estimated_delivery_date,
                                       total_amount, pass_through_amount, deposit_amount, status)
                VALUES (?, 1, (SELECT id FROM price_list WHERE name = 'Bảng giá mẫu (dev)'), 'VND', 0.3, now() + interval '1 day',
                        DATE '2026-12-13', DATE '2026-12-20', DATE '2026-12-25', 10000000, 1000000, 500000, 'DRAFT')""", bookingId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_quotation_deposit_amount");
    }

    @Test
    void quotationLineAmountIsComputedByDatabase() {
        long quotationId = newQuotation(newBooking());
        jdbc.update("""
                INSERT INTO quotation_line (quotation_id, line_no, line_type, service_id, description, quantity, unit, unit_price, is_pass_through)
                VALUES (?, 1, 'LINEHAUL', (SELECT id FROM service WHERE code = 'LINEHAUL_STALL'), 'Cước trục 2 khoang', 2, 'STALL', 6000000.50, false)""",
                quotationId);

        BigDecimal amount = jdbc.queryForObject("SELECT amount FROM quotation_line WHERE quotation_id = ?", BigDecimal.class, quotationId);
        assertThat(amount).isEqualByComparingTo("12000001.00");
    }

    @Test
    void horseLegNeedsExactlyOneCarrier() {
        long bookingHorseId = newBookingHorse(newBooking());
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO horse_leg (booking_horse_id, seq, stage, from_location_id, to_location_id, planned_start, planned_end, status)
                VALUES (?, 1, 'LINEHAUL', (SELECT id FROM location WHERE code = 'HUB-HCM'), (SELECT id FROM location WHERE code = 'GATE-MOCBAI'),
                        TIMESTAMPTZ '2026-12-20 06:00+07', TIMESTAMPTZ '2026-12-20 08:00+07', 'PLANNED')""", bookingHorseId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_horse_leg_carrier");
    }

    @Test
    void documentBelongsToExactlyOneOwner() {
        long bookingId = newBooking();
        long bookingHorseId = newBookingHorse(bookingId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, booking_id, booking_horse_id)
                VALUES ((SELECT id FROM document_type WHERE code = 'HEALTH_CERT'), 'https://files.test/health.pdf', 'health.pdf', ?, 'UPLOADED', ?, ?)""",
                userId("customer1.dev"), bookingId, bookingHorseId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_document_owner");
    }
}
