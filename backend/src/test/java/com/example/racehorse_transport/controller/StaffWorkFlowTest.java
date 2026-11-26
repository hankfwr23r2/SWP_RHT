package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import java.time.LocalDate;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestData.class, BenXeJourneyFlowTest.Database.class})
@AutoConfigureMockMvc
@Transactional
class StaffWorkFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwt;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BookingStatusService statuses;
    @Autowired private BookingRepository bookings;
    @Autowired private DepartureRepository departures;

    @Test
    void managerReceivesActionableOrderCounts() throws Exception {
        var booking = data.booking(data.customer(data.unique("u3.customer")), BookingType.LOCAL, BookingStatus.PENDING_INTAKE);
        int pending = count("select count(*) from booking where status = 'PENDING_INTAKE'");
        int workable = count("select count(*) from booking where status in ('PENDING_INTAKE', 'QUOTING', 'DELIVERED')");
        summary(token("manager.dev", "MANAGER")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filterCounts.intake").value(pending))
                .andExpect(jsonPath("$.data.menuCounts.orders").value(workable + overdueDocuments()));
        statuses.recordInitialStatus(booking);
        assertNotice(token("manager.dev", "MANAGER"), "Đơn mới chờ tiếp nhận", "/manager/orders/" + booking.getId(), 1);
    }

    @Test
    void coordinatorReceivesPlanAndReportedIncidentCounts() throws Exception {
        var coordinator = data.staff(data.unique("u3.coord"), "COORDINATOR");
        int reported = count("select count(*) from incident where status = 'REPORTED' and incident_type <> 'COMPLAINT'");
        int review = count("select count(*) from booking where status = 'UNDER_REVIEW'");
        summary(token(coordinator.getUser().getUsername(), "COORDINATOR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuCounts.incidents").value(reported))
                .andExpect(jsonPath("$.data.menuCounts.orders").doesNotExist())
                .andExpect(jsonPath("$.data.filterCounts.UNDER_REVIEW").value(review));
    }

    @Test
    void specialistReceivesEmptySummaryAndCustomerIsForbidden() throws Exception {
        var specialist = data.staff(data.unique("u3.specialist"), "SPECIALIST");
        summary(token(specialist.getUser().getUsername(), "SPECIALIST")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuCounts").isEmpty())
                .andExpect(jsonPath("$.data.filterCounts").isEmpty());
        summary(token("customer1.dev", "CUSTOMER")).andExpect(status().isForbidden());
    }

    @Test
    void managerReceivesQuoteAndCustomerCancellationEvents() throws Exception {
        var customer = data.customer(data.unique("u3.quote"));
        var booking = data.booking(customer, BookingType.LOCAL, BookingStatus.UNDER_REVIEW);
        var manager = token("manager.dev", "MANAGER");
        var link = "/manager/orders/" + booking.getId();

        statuses.transition(booking.getId(), BookingStatus.QUOTING, null, null);
        assertNotice(manager, "Đơn chờ lập báo giá", link, 1);
        statuses.transition(booking.getId(), BookingStatus.AWAITING_DEPOSIT, null, null);
        statuses.transition(booking.getId(), BookingStatus.QUOTING, "Khách từ chối", customer.getUserId());
        assertNotice(manager, "Khách từ chối báo giá", link, 1);
        statuses.transition(booking.getId(), BookingStatus.AWAITING_DEPOSIT, null, null);
        statuses.expireQuote(booking.getId(), null);
        assertNotice(manager, "Báo giá hết hạn, xem xét mở lại", link, 1);

        var cancelled = data.booking(customer, BookingType.LOCAL, BookingStatus.PENDING_INTAKE);
        statuses.transition(cancelled.getId(), BookingStatus.CANCELLED, "Khách hủy", customer.getUserId());
        assertNotice(manager, "Khách hủy đơn trước cọc", "/manager/orders/" + cancelled.getId(), 1);
    }

    @Test
    void depositNotifiesManagerAndOnlyTheAssignedCoordinator() throws Exception {
        var owner = data.staff(data.unique("u3.assigned"), "COORDINATOR");
        var other = data.staff(data.unique("u3.other"), "COORDINATOR");
        var depart = data.departure("HCM-DN", LocalDate.of(2027, 2, 25), 6);
        depart.setCoordinator(owner);
        departures.saveAndFlush(depart);
        var booking = data.booking(data.customer(data.unique("u3.deposit")), BookingType.INTERPROVINCE, BookingStatus.AWAITING_DEPOSIT);
        booking.setDeparture(depart);
        booking.setWaybillNumber(data.unique("WB-U3"));
        bookings.saveAndFlush(booking);

        statuses.transition(booking.getId(), BookingStatus.CONFIRMED, null, null);
        assertNotice(token("manager.dev", "MANAGER"), "Khách đã đặt cọc", "/manager/orders/" + booking.getId(), 1);
        var link = "/coordinator/dispatch?bookingId=" + booking.getId();
        assertNotice(token(owner.getUser().getUsername(), "COORDINATOR"), "Đơn đã xác nhận chờ xếp xe", link, 1);
        assertNotice(token(other.getUser().getUsername(), "COORDINATOR"), "Đơn đã xác nhận chờ xếp xe", link, 0);

        statuses.transition(booking.getId(), BookingStatus.CANCELLED, "Khách hủy", booking.getCustomer().getUserId());
        assertNotice(token("manager.dev", "MANAGER"), "Khách hủy đơn đã cọc", "/manager/orders/" + booking.getId(), 1);
    }

    private void assertNotice(String token, String title, String link, int count) throws Exception {
        String selector = "$.data[?(@.title == '" + title + "' && @.linkUrl == '" + link + "')]";
        mvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath(selector, hasSize(count)));
    }

    private int overdueDocuments() throws Exception {
        var response = mvc.perform(get("/bookings/overdue-documents")
                .header(HttpHeaders.AUTHORIZATION, token("manager.dev", "MANAGER")))
                .andExpect(status().isOk()).andReturn().getResponse();
        return ((Number) com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.data.length()")).intValue();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String token(String username, String role) {
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }

    private ResultActions summary(String token) throws Exception {
        return mvc.perform(get("/staff/work-summary").header(HttpHeaders.AUTHORIZATION, token));
    }
}
