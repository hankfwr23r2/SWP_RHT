package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

// V202611231200__account_profiles.sql trên DB ĐÃ CÓ dữ liệu: migrate tới phiên bản trước, chèn mẫu, migrate tiếp.
// Mỗi test dùng một database riêng trong cùng một container, không khởi động Spring.
class AccountProfilesMigrationTest {

    private static final String BEFORE = "202611231100";
    private static final String ACCOUNT_PROFILES = "202611231200";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static PostgreSQLContainer postgres;

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    static void startPostgres() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        postgres.stop();
    }

    @BeforeEach
    void migrateToPreviousVersion() {
        String database = "profiles_" + SEQUENCE.incrementAndGet();
        new JdbcTemplate(dataSource(postgres.getDatabaseName())).execute("CREATE DATABASE " + database);
        dataSource = dataSource(database);
        jdbc = new JdbcTemplate(dataSource);
        migrateTo(BEFORE);
    }

    // ----- Chuyển dữ liệu -----

    @Test
    void movesCustomersAndKeepsEveryReference() {
        // Tạo đối tác và party của khách B trước, để party.id lệch với app_user.id và chéo nhau giữa hai khách
        long agent = partner("Đại lý", "DEST_AGENT");
        long unassigned = party("Đối tác chưa gán vai trò", null);
        long userA = user("khach.a", "CUSTOMER");
        long userB = user("khach.b", "CUSTOMER");
        long partyB = customerParty("Khách B", userB);
        long partyA = customerParty("Khách A", userA);
        long staff = staff("dieuphoi.a");
        long consignee = partner("Người nhận", "CONSIGNEE");
        jdbc.update("UPDATE party SET id_type = 'CCCD', id_number = '079000000001', id_country = 'VN', tax_code = '0312345678' WHERE id = ?", partyA);
        long addressA = address(partyA, "Nhà A");
        long addressB = address(partyB, "Nhà B");
        long horseA = horse(partyA, "Sao Mai");
        long bookingB = booking("BK-B", partyB, userB);
        long bookingA = booking("BK-A", partyA, userA);
        jdbc.update("INSERT INTO booking_party (booking_id, role, party_id, full_name, phone, address) VALUES (?, 'PAYER', ?, 'Khách A', '0909', 'HCM')", bookingA, partyA);
        bookingAddress(bookingA, addressA);
        long documentOfCustomer = document("POWER_OF_ATTORNEY", partyA, userA);
        long documentOfAgent = document("PARTNER_CONTRACT", agent, staff);
        long stay = stay(bookingA, horseA);
        long pickup = handover("PICKUP", stay, partyA, null, staff);
        long toAgent = handover("AGENT", stay, null, agent, staff);
        long delivery = handover("DELIVERY", stay, agent, consignee, staff);

        migrateTo(ACCOUNT_PROFILES);

        assertThat(jdbc.queryForMap("SELECT * FROM customer WHERE user_id = ?", userA))
                .containsEntry("full_name", "Khách A")
                .containsEntry("customer_type", "PERSON")
                .containsEntry("country_code", "VN")
                .containsEntry("identity_document_type", "CCCD")
                .containsEntry("identity_document_number", "079000000001")
                .containsEntry("identity_document_country_code", "VN")
                .containsEntry("tax_code", "0312345678");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer", Integer.class)).isEqualTo(2);
        assertThat(value("SELECT role_code FROM app_user WHERE id = ?", userA)).isEqualTo("CUSTOMER");
        assertThat(value("SELECT role_code FROM app_user WHERE id = ?", staff)).isEqualTo("COORDINATOR");

        assertThat(value("SELECT customer_id FROM booking WHERE id = ?", bookingA)).isEqualTo(userA);
        assertThat(value("SELECT customer_id FROM booking WHERE id = ?", bookingB)).isEqualTo(userB);
        assertThat(value("SELECT owner_customer_id FROM horse WHERE id = ?", horseA)).isEqualTo(userA);
        assertThat(value("SELECT customer_id FROM customer_address WHERE id = ?", addressA)).isEqualTo(userA);
        assertThat(value("SELECT customer_id FROM customer_address WHERE id = ?", addressB)).isEqualTo(userB);
        assertThat(value("SELECT source_customer_address_id FROM booking_address WHERE booking_id = ?", bookingA)).isEqualTo(addressA);
        assertThat(jdbc.queryForMap("SELECT party_id, customer_id FROM document WHERE id = ?", documentOfCustomer))
                .containsEntry("customer_id", userA).containsEntry("party_id", null);
        assertThat(jdbc.queryForMap("SELECT party_id, customer_id FROM document WHERE id = ?", documentOfAgent))
                .containsEntry("customer_id", null).containsEntry("party_id", agent);

        // Giao nhận: khách và người nhận thành NULL, đối tác giữ nguyên
        assertThat(handoverParties(pickup)).containsEntry("from_party_id", null).containsEntry("to_party_id", null);
        assertThat(handoverParties(toAgent)).containsEntry("from_party_id", null).containsEntry("to_party_id", agent);
        assertThat(handoverParties(delivery)).containsEntry("from_party_id", agent).containsEntry("to_party_id", null);

        // Chỉ còn đối tác, kể cả đối tác chưa gán vai trò
        assertThat(jdbc.queryForList("SELECT id FROM party ORDER BY id", Long.class)).containsExactly(agent, unassigned);
        assertThat(jdbc.queryForList("SELECT role_type FROM party_role", String.class)).containsExactly("DEST_AGENT");
        assertThat(jdbc.queryForObject("SELECT to_regclass('user_role') IS NULL AND to_regclass('party_address') IS NULL", Boolean.class)).isTrue();
        // Cột id của sổ địa chỉ vẫn tự tăng sau khi đổi tên bảng
        assertThat(id("INSERT INTO customer_address (customer_id, label, address_line, province_code, country_code) VALUES (?, 'Kho', 'HCM', 'VN-79', 'VN') RETURNING id", userA))
                .isGreaterThan(addressB);
    }

    @Test
    void partyThatIsCustomerAndPartnerKeepsBothProfiles() {
        long user = user("khach.doi", "CUSTOMER");
        long party = customerParty("CLB vừa là khách vừa là chuồng gửi", user);
        jdbc.update("INSERT INTO party_role (party_id, role_type, valid_from) VALUES (?, 'STABLE', DATE '2026-01-01')", party);

        migrateTo(ACCOUNT_PROFILES);

        assertThat(value("SELECT full_name FROM customer WHERE user_id = ?", user)).isEqualTo("CLB vừa là khách vừa là chuồng gửi");
        assertThat(jdbc.queryForList("SELECT role_type FROM party_role WHERE party_id = ?", String.class, party)).containsExactly("STABLE");
    }

    @Test
    void newConstraintsRejectWrongReferences() {
        long user = user("khach.c", "CUSTOMER");
        customerParty("Khách C", user);
        long staff = staff("dieuphoi.c");

        migrateTo(ACCOUNT_PROFILES);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO booking (code, customer_id, booking_type, requested_pickup_date, status, created_by)
                VALUES ('BK-SAI', ?, 'LOCAL', DATE '2027-01-01', 'PENDING_INTAKE', ?)""", staff, staff))
                .hasMessageContaining("fk_booking_customer_id");
        assertThatThrownBy(() -> jdbc.update("UPDATE customer SET identity_document_type = 'CCCD' WHERE user_id = ?", user))
                .hasMessageContaining("ck_customer_identity_complete");
        assertThatThrownBy(() -> jdbc.update("UPDATE app_user SET role_code = NULL WHERE id = ?", user))
                .hasMessageContaining("role_code");
    }

    // ----- Dữ liệu sai: migration dừng và không đổi gì -----

    @Test
    void stopsWhenAccountHasNoRole() {
        user("khongvaitro", null);
        assertStopsWith("phải có đúng một vai trò");
    }

    @Test
    void stopsWhenAccountHasTwoRoles() {
        long staff = staff("haivaitro");
        jdbc.update("INSERT INTO user_role (user_id, role_code) VALUES (?, 'MANAGER')", staff);
        assertStopsWith("phải có đúng một vai trò");
    }

    @Test
    void stopsWhenCustomerHasNoParty() {
        user("khachkhonghoso", "CUSTOMER");
        assertStopsWith("tài khoản khách không có party");
    }

    @Test
    void stopsWhenCustomerAlsoHasStaffProfile() {
        long user = user("khachnhanvien", "CUSTOMER");
        customerParty("Khách kiêm nhân viên", user);
        staffProfile(user);
        assertStopsWith("tài khoản khách có hồ sơ nhân viên");
    }

    @Test
    void stopsWhenInternalAccountHasNoStaffProfile() {
        user("quanlykhonghoso", "MANAGER");
        assertStopsWith("tài khoản nội bộ không có hồ sơ nhân viên");
    }

    @Test
    void stopsWhenInactiveCustomerWouldBecomeActive() {
        long user = user("khachngung", "CUSTOMER");
        jdbc.update("UPDATE party SET status = 'INACTIVE' WHERE id = ?", customerParty("Khách ngừng", user));
        assertStopsWith("khách INACTIVE nhưng tài khoản còn ACTIVE");
    }

    @Test
    void stopsWhenBookingBelongsToPartyWithoutAccount() {
        long user = user("khach.d", "CUSTOMER");
        customerParty("Khách D", user);
        booking("BK-D", partner("Phòng khám", "VET"), user);
        assertStopsWith("người đặt đơn không có tài khoản khách");
    }

    @Test
    void stopsWhenHorseBelongsToStaffAccount() {
        horse(customerParty("Hồ sơ của nhân viên", staff("nhanvien.e")), "Gió Nam");
        assertStopsWith("chủ ngựa không có tài khoản khách");
    }

    @Test
    void stopsWhenAddressBelongsToPartner() {
        address(partner("Chuồng gửi", "STABLE"), "Kho");
        assertStopsWith("địa chỉ không thuộc tài khoản khách");
    }

    @Test
    void stopsWhenDocumentOwnerIsCustomerAndPartner() {
        long user = user("khach.f", "CUSTOMER");
        long party = customerParty("Khách F", user);
        jdbc.update("INSERT INTO party_role (party_id, role_type, valid_from) VALUES (?, 'STABLE', DATE '2026-01-01')", party);
        document("PARTNER_CONTRACT", party, user);
        assertStopsWith("vừa là khách vừa là đối tác");
    }

    @Test
    void stopsWhenCustomerDocumentHasPartnerType() {
        long user = user("khach.g", "CUSTOMER");
        document("PARTNER_CONTRACT", customerParty("Khách G", user), user);
        assertStopsWith("loại giấy dành cho đối tác");
    }

    @Test
    void stopsWhenDocumentBelongsToInternalAccountParty() {
        long staff = staff("nhanvien.h");
        document("POWER_OF_ATTORNEY", customerParty("Hồ sơ của nhân viên", staff), staff);
        assertStopsWith("chứng từ gắn party của tài khoản nội bộ");
    }

    // ----- Hàm dựng -----

    private void assertStopsWith(String message) {
        assertThatThrownBy(() -> migrateTo(ACCOUNT_PROFILES)).isInstanceOf(FlywayException.class).hasMessageContaining(message);
        // Toàn bộ file được hoàn tác
        assertThat(jdbc.queryForObject("SELECT to_regclass('customer') IS NULL AND to_regclass('user_role') IS NOT NULL", Boolean.class)).isTrue();
    }

    private DriverManagerDataSource dataSource(String database) {
        String url = "jdbc:postgresql://%s:%d/%s".formatted(postgres.getHost(), postgres.getMappedPort(5432), database);
        return new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
    }

    private long id(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        assertThat(value).isNotNull();
        return value;
    }

    private Object value(String sql, Object... args) {
        return jdbc.queryForObject(sql, Object.class, args);
    }

    private Map<String, Object> handoverParties(long handoverId) {
        return jdbc.queryForMap("SELECT from_party_id, to_party_id FROM handover WHERE id = ?", handoverId);
    }

    private long user(String username, String roleCode) {
        long user = id("INSERT INTO app_user (username, email, password_hash, status) VALUES (?, ?, 'x', 'ACTIVE') RETURNING id",
                username, username + "@test.racehorse");
        if (roleCode != null) {
            jdbc.update("INSERT INTO user_role (user_id, role_code) VALUES (?, ?)", user, roleCode);
        }
        return user;
    }

    private long staff(String username) {
        long user = user(username, "COORDINATOR");
        staffProfile(user);
        return user;
    }

    private void staffProfile(long user) {
        jdbc.update("""
                INSERT INTO staff (user_id, staff_code, full_name, phone, hire_date, employment_status, home_hub_id)
                VALUES (?, ?, 'Nhân viên', '0900', DATE '2026-01-01', 'ACTIVE', ?)""", user, "STF-" + user, hub());
    }

    private long hub() {
        jdbc.update("""
                INSERT INTO location (code, name, type, country_code, province_code, address, status)
                VALUES ('HUB-TEST', 'Hub', 'HUB', 'VN', 'VN-79', 'HCM', 'ACTIVE') ON CONFLICT DO NOTHING""");
        return id("SELECT id FROM location WHERE code = 'HUB-TEST'");
    }

    private long party(String name, Long user) {
        return id("INSERT INTO party (kind, display_name, country_code, user_id, status) VALUES ('PERSON', ?, 'VN', ?, 'ACTIVE') RETURNING id", name, user);
    }

    private long partner(String name, String roleType) {
        long party = party(name, null);
        jdbc.update("INSERT INTO party_role (party_id, role_type, valid_from) VALUES (?, ?, DATE '2026-01-01')", party, roleType);
        return party;
    }

    private long customerParty(String name, long user) {
        long party = party(name, user);
        jdbc.update("INSERT INTO party_role (party_id, role_type, valid_from) VALUES (?, 'CUSTOMER', DATE '2026-01-01')", party);
        return party;
    }

    private long address(long party, String label) {
        return id("""
                INSERT INTO party_address (party_id, label, address_line, province_code, country_code, is_default)
                VALUES (?, ?, 'HCM', 'VN-79', 'VN', true) RETURNING id""", party, label);
    }

    private long horse(long ownerParty, String name) {
        return id("""
                INSERT INTO horse (owner_party_id, name, gender, horse_type_id, status)
                VALUES (?, ?, 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE') RETURNING id""", ownerParty, name);
    }

    private long booking(String code, long customerParty, long createdBy) {
        return id("""
                INSERT INTO booking (code, customer_party_id, booking_type, requested_pickup_date, status, created_by)
                VALUES (?, ?, 'LOCAL', DATE '2027-01-01', 'PENDING_INTAKE', ?) RETURNING id""", code, customerParty, createdBy);
    }

    private void bookingAddress(long booking, long sourceAddress) {
        jdbc.update("""
                INSERT INTO booking_address (booking_id, role, address_line, province_code, country_code, latitude, longitude,
                                             contact_name, contact_phone, source_address_id)
                VALUES (?, 'PICKUP', 'HCM', 'VN-79', 'VN', 10.8, 106.7, 'Anh Ba', '0909', ?)""", booking, sourceAddress);
    }

    private long stay(long booking, long horse) {
        long line = id("INSERT INTO booking_horse (booking_id, horse_id, stall_type, status) VALUES (?, ?, 'STANDARD', 'ACTIVE') RETURNING id", booking, horse);
        return id("""
                INSERT INTO horse_stay (booking_horse_id, kind, location_id, planned_from, planned_to)
                VALUES (?, 'ASSEMBLY', ?, now(), now() + interval '1 day') RETURNING id""", line, hub());
    }

    private long handover(String kind, long stay, Long from, Long to, long staff) {
        return id("""
                INSERT INTO handover (kind, horse_stay_id, from_party_id, to_party_id, staff_id, status)
                VALUES (?, ?, ?, ?, ?, 'PENDING') RETURNING id""", kind, stay, from, to, staff);
    }

    private long document(String typeCode, long party, long uploadedBy) {
        return id("""
                INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, party_id)
                VALUES ((SELECT id FROM document_type WHERE code = ?), ?, 'a.pdf', ?, 'UPLOADED', ?) RETURNING id""",
                typeCode, "https://files.test/" + typeCode + "-" + party + ".pdf", uploadedBy, party);
    }
}
