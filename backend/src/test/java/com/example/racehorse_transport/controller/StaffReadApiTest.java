package com.example.racehorse_transport.controller;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Các API chỉ đọc phục vụ màn hình: danh sách đơn, khoản thu, khoản hoàn, hãng bảo hiểm, loại khoang, xem trước tiền hủy
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class StaffReadApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwt;

    @Test
    void summariesFilterByStatusAndCountOnlyActiveHorses() throws Exception {
        var customer = data.customer(data.unique("read-customer"));
        var booking = data.booking(customer, BookingType.INTERPROVINCE, BookingStatus.QUOTING);
        var departure = data.departure("HCM-DN", LocalDate.of(2031, 5, 6), 9);
        booking.setDeparture(departure);
        data.booking(customer, BookingType.LOCAL, BookingStatus.PENDING_INTAKE);
        horse(booking.getId(), customer.getUser().getId(), "ACTIVE");
        horse(booking.getId(), customer.getUser().getId(), "EXCLUDED");

        mvc.perform(get("/bookings/summaries").param("statuses", "QUOTING").header(HttpHeaders.AUTHORIZATION, staff("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].status", everyItem(is("QUOTING"))))
                .andExpect(jsonPath("$.data[0].code").value(booking.getCode()))
                .andExpect(jsonPath("$.data[0].customerName").value(customer.getFullName()))
                .andExpect(jsonPath("$.data[0].horseCount").value(1))
                .andExpect(jsonPath("$.data[0].routeName").value(departure.getRoute().getName()));
        mvc.perform(get("/bookings/summaries").param("statuses", "QUOTING,PENDING_INTAKE").param("page", "500")
                        .header(HttpHeaders.AUTHORIZATION, staff("SPECIALIST")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", empty()));
        mvc.perform(get("/bookings/summaries").param("statuses", "NOPE").header(HttpHeaders.AUTHORIZATION, staff("MANAGER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/bookings/summaries").param("statuses", "QUOTING")
                        .header(HttpHeaders.AUTHORIZATION, customer(customer.getUser().getUsername())))
                .andExpect(status().isForbidden());

        // Khách chỉ thấy đơn của mình, cùng dữ liệu tóm tắt
        mvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, customer(customer.getUser().getUsername())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == %d)].horseCount".formatted(booking.getId())).value(1))
                .andExpect(jsonPath("$.data[?(@.id == %d)].departureDate".formatted(booking.getId())).value("2031-05-06"))
                .andExpect(jsonPath("$.data[?(@.id == %d)].routeName".formatted(booking.getId())).value(departure.getRoute().getName()));
        String other = data.unique("read-other");
        data.customer(other);
        mvc.perform(get("/bookings").header(HttpHeaders.AUTHORIZATION, customer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", empty()));
    }

    @Test
    void managerSeesPaymentsAndPendingRefunds() throws Exception {
        var customer = data.customer(data.unique("read-customer"));
        var booking = data.booking(customer, BookingType.LOCAL, BookingStatus.CANCELLED);
        Long deposit = jdbc.queryForObject("""
                INSERT INTO payment (booking_id, payment_type, amount, currency_code, payment_method, status, paid_at)
                VALUES (?, 'EXTRA_CHARGE', 5000, 'VND', 'VNPAY', 'PAID', now()) RETURNING id""", Long.class, booking.getId());
        jdbc.update("""
                INSERT INTO payment (booking_id, refunded_payment_id, payment_type, amount, currency_code, payment_method, status)
                VALUES (?, ?, 'REFUND', 2000, 'VND', 'BANK_TRANSFER', 'PENDING')""", booking.getId(), deposit);
        String manager = staff("MANAGER");

        mvc.perform(get("/bookings/{id}/payments", booking.getId()).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/bookings/{id}/payments", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isNotFound());
        mvc.perform(get("/bookings/{id}/payments", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, customer(otherCustomer())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/refunds").header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].bookingCode", hasItem(booking.getCode())))
                .andExpect(jsonPath("$.data[*].status", everyItem(is("PENDING"))));
        mvc.perform(get("/refunds").param("status", "PAID").header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(jsonPath("$.data[*].bookingCode", not(hasItem(booking.getCode()))));
        mvc.perform(get("/refunds").header(HttpHeaders.AUTHORIZATION, staff("COORDINATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerReadsInsurersWithoutContactsAndStallTypesWithSeats() throws Exception {
        String token = customer(otherCustomer());
        String expired = data.unique("Hãng cũ");
        Long party = jdbc.queryForObject("""
                INSERT INTO party (party_type, display_name, phone, country_code, status)
                VALUES ('ORGANIZATION', ?, '0900', 'VN', 'ACTIVE') RETURNING id""", Long.class, expired);
        jdbc.update("INSERT INTO party_role (party_id, role_type, valid_from, valid_to) VALUES (?, 'INSURER', '2020-01-01', '2020-12-31')", party);

        mvc.perform(get("/parties/insurers").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].displayName", hasItem("Công ty bảo hiểm (mẫu)")))
                .andExpect(jsonPath("$.data[*].displayName", not(hasItem(expired))))
                .andExpect(jsonPath("$.data[0].phone").doesNotExist());
        mvc.perform(get("/parties").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(get("/stall-types").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'WIDE')].seats", hasItem(2)))
                .andExpect(jsonPath("$.data[?(@.code == 'STANDARD')].seats", hasItem(1)));
        mvc.perform(get("/stall-types")).andExpect(status().isUnauthorized());
    }

    @Test
    void reviewDetailShowsWhoTookTheOrderInOnlyToStaff() throws Exception {
        var customer = data.customer(data.unique("read-customer"));
        var booking = data.booking(customer, BookingType.LOCAL, BookingStatus.UNDER_REVIEW);
        var taker = data.staff(data.unique("read-taker"), "SPECIALIST");
        String manager = staff("MANAGER");
        mvc.perform(get("/bookings/{id}/review-detail", booking.getId()).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.intakeByName").doesNotExist());

        jdbc.update("""
                INSERT INTO booking_status_history (booking_id, from_status, to_status, changed_by)
                VALUES (?, 'PENDING_INTAKE', 'UNDER_REVIEW', ?)""", booking.getId(), taker.getUserId());
        // Khách nộp lại hồ sơ cũng về UNDER_REVIEW nhưng không phải người tiếp nhận
        jdbc.update("""
                INSERT INTO booking_status_history (booking_id, from_status, to_status, changed_by)
                VALUES (?, 'NEEDS_RESUBMISSION', 'UNDER_REVIEW', ?)""", booking.getId(), customer.getUserId());
        mvc.perform(get("/bookings/{id}/review-detail", booking.getId()).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(jsonPath("$.data.intakeByName").value(taker.getFullName()));
        mvc.perform(get("/bookings/{id}", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, customer(customer.getUser().getUsername())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.intakeByName").doesNotExist());
    }

    @Test
    void managerPreviewsCancellationWithoutChangingTheBooking() throws Exception {
        var customer = data.customer(data.unique("read-customer"));
        var booking = data.booking(customer, BookingType.LOCAL, BookingStatus.UNDER_REVIEW);

        mvc.perform(get("/bookings/{id}/manager-cancellation-preview", booking.getId()).param("byCompany", "true")
                        .header(HttpHeaders.AUTHORIZATION, staff("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));
        mvc.perform(get("/bookings/{id}/manager-cancellation-preview", booking.getId()).param("byCompany", "false")
                        .header(HttpHeaders.AUTHORIZATION, customer(customer.getUser().getUsername())))
                .andExpect(status().isForbidden());
    }

    private void horse(Long booking, Long owner, String status) {
        Long horse = jdbc.queryForObject("""
                INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                SELECT ?, ?, 'MARE', min(id), 'ACTIVE' FROM horse_type RETURNING id""", Long.class, owner, data.unique("read-horse"));
        jdbc.update("INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status) VALUES (?, ?, 'STANDARD', ?)",
                booking, horse, status);
    }

    private String otherCustomer() {
        String username = data.unique("read-other");
        data.customer(username);
        return username;
    }

    private String customer(String username) {
        return "Bearer " + jwt.generateToken(username, List.of("CUSTOMER"));
    }

    private String staff(String role) {
        String username = data.unique("read-staff");
        data.staff(username, role);
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }
}
