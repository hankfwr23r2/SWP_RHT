package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// B6: báo cáo doanh thu của quản lý dựng từ payment, quotation, cost_item
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class RevenueReportFlowTest {

    // Ngày 15/01/2031 theo giờ Việt Nam, ngoài mọi dữ liệu mẫu
    private static final String FROM = "2031-01-14T17:00:00Z";
    private static final String TO = "2031-01-15T17:00:00Z";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;

    private String manager;
    private String coordinator;

    @BeforeEach
    void setUp() {
        Staff coordinatorStaff = testData.staff(testData.unique("revenue.coordinator"), "COORDINATOR");
        coordinator = token(coordinatorStaff, "COORDINATOR");
        manager = token(testData.staff(testData.unique("revenue.manager"), "MANAGER"), "MANAGER");
        long direct = bookingId("DEV-DIRECT-001");
        long domestic = bookingId("DEV-DOMESTIC-001");

        // Đơn đang chạy: báo giá 10 triệu (1 triệu thu hộ), cọc 3 triệu trong ngày, số dư 7 triệu hôm sau, phụ thu 500 nghìn
        jdbc.update("UPDATE quotation SET total_amount = 10000000, pass_through_amount = 1000000, deposit_amount = 3000000 WHERE booking_id = ? AND status = 'ACCEPTED'", direct);
        jdbc.update("UPDATE payment SET amount = 3000000, paid_at = '2031-01-15T03:00:00Z' WHERE booking_id = ? AND payment_type = 'DEPOSIT'", direct);
        jdbc.update("UPDATE payment SET amount = 7000000, paid_at = '2031-01-16T03:00:00Z' WHERE booking_id = ? AND payment_type = 'BALANCE'", direct);
        jdbc.update("""
                INSERT INTO payment (booking_id, payment_type, amount, currency_code, payment_method, status, paid_at)
                VALUES (?, 'EXTRA_CHARGE', 500000, 'VND', 'VNPAY', 'PAID', '2031-01-17T03:00:00Z')""", direct);

        // Đơn đã hủy: cọc 3 triệu, chưa trả số dư, hoàn 1 triệu, 500 nghìn thu hộ không hoàn
        jdbc.update("UPDATE booking SET status = 'CANCELLED' WHERE id = ?", domestic);
        jdbc.update("UPDATE quotation SET pass_through_amount = 500000, deposit_amount = greatest(deposit_amount, 500000) WHERE booking_id = ? AND status = 'ACCEPTED'", domestic);
        jdbc.update("UPDATE payment SET amount = 3000000, paid_at = '2031-01-15T05:00:00Z' WHERE booking_id = ? AND payment_type = 'DEPOSIT'", domestic);
        jdbc.update("UPDATE payment SET status = 'PENDING', paid_at = NULL WHERE booking_id = ? AND payment_type = 'BALANCE'", domestic);
        jdbc.update("""
                INSERT INTO payment (booking_id, quotation_id, payment_type, amount, currency_code, payment_method, status, paid_at, refunded_payment_id)
                SELECT booking_id, quotation_id, 'REFUND', 1000000, 'VND', 'BANK_TRANSFER', 'PAID', '2031-01-20T03:00:00Z', id
                FROM payment WHERE booking_id = ? AND payment_type = 'DEPOSIT'""", domestic);

        // Chi phí: chỉ khoản công ty chịu đã duyệt trong ngày được tính
        Long staff = coordinatorStaff.getUserId();
        cost("COMPANY", 300000, direct, staff, true);
        cost("COMPANY", 100000, direct, staff, false);
        cost("CUSTOMER", 200000, direct, staff, true);
    }

    @Test
    void revenueCountsBookingsByDepositDateWithoutPassThroughAndCompanyCosts() throws Exception {
        revenue(manager, FROM, TO).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revenue").value(11000000.0))
                .andExpect(jsonPath("$.data.collected").value(12500000.0))
                .andExpect(jsonPath("$.data.passThrough").value(1500000.0))
                .andExpect(jsonPath("$.data.companyCost").value(300000.0))
                .andExpect(jsonPath("$.data.profit").value(10700000.0))
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.days[0].date").value("2031-01-15"))
                .andExpect(jsonPath("$.data.days[0].revenue").value(11000000.0))
                .andExpect(jsonPath("$.data.days[0].cost").value(300000.0))
                .andExpect(jsonPath("$.data.bookings.length()").value(2))
                .andExpect(jsonPath("$.data.bookings[0].code").value("DEV-DOMESTIC-001"))
                .andExpect(jsonPath("$.data.bookings[0].revenue").value(1500000.0))
                .andExpect(jsonPath("$.data.bookings[0].collected").value(2000000.0))
                .andExpect(jsonPath("$.data.bookings[1].code").value("DEV-DIRECT-001"))
                .andExpect(jsonPath("$.data.bookings[1].revenue").value(9500000.0))
                .andExpect(jsonPath("$.data.bookings[1].collected").value(10500000.0));
    }

    @Test
    void onlyManagersSeeRevenueAndRangeMustBeForward() throws Exception {
        revenue(coordinator, FROM, TO).andExpect(status().isForbidden());
        revenue(manager, TO, FROM).andExpect(status().is4xxClientError());
    }

    private void cost(String payer, long amount, long booking, Long staff, boolean approved) {
        jdbc.update("""
                INSERT INTO cost_item (category, description, amount, currency_code, payer_type, booking_id, incurred_at, recorded_by, approved_by, approved_at)
                VALUES ('TOLL', 'Phí cầu đường', ?, 'VND', ?, ?, '2031-01-15T04:00:00Z', ?, ?, ?)""",
                amount, payer, booking, staff, approved ? staff : null, approved ? java.sql.Timestamp.from(java.time.Instant.parse("2031-01-15T04:00:00Z")) : null);
    }

    private long bookingId(String code) {
        return jdbc.queryForObject("SELECT id FROM booking WHERE code = ?", Long.class, code);
    }

    private ResultActions revenue(String token, String from, String to) throws Exception {
        return mvc.perform(get("/reports/revenue").param("from", from).param("to", to).header(HttpHeaders.AUTHORIZATION, token));
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role));
    }
}
