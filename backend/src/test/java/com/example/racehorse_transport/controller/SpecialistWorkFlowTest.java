package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class SpecialistWorkFlowTest {
    @Autowired private TestData data;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwt;

    @Test
    void countsOnlyTheSpecialistsOwnBookingsAndRejectsOtherRoles() throws Exception {
        Customer customer = data.customer(data.unique("work.customer"));
        Staff mine = data.staff(data.unique("work.specialist"), "SPECIALIST");
        Staff other = data.staff(data.unique("work.other"), "SPECIALIST");
        assign(data.booking(customer, BookingType.INTERPROVINCE, BookingStatus.UNDER_REVIEW), mine);
        assign(data.booking(customer, BookingType.INTERPROVINCE, BookingStatus.UNDER_REVIEW), mine);
        assign(data.booking(customer, BookingType.INTERPROVINCE, BookingStatus.NEEDS_RESUBMISSION), mine);
        assign(data.booking(customer, BookingType.INTERPROVINCE, BookingStatus.UNDER_REVIEW), other);
        // Đơn chưa giao chuyên viên không tính cho ai
        data.booking(customer, BookingType.INTERPROVINCE, BookingStatus.UNDER_REVIEW);
        entityManager.flush();
        entityManager.clear();

        summary(mine.getUser().getUsername(), "SPECIALIST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationPending").value(2))
                .andExpect(jsonPath("$.data.waitingCustomer").value(1));
        summary(other.getUser().getUsername(), "SPECIALIST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationPending").value(1))
                .andExpect(jsonPath("$.data.waitingCustomer").value(0));

        String manager = data.unique("work.manager");
        data.staff(manager, "MANAGER");
        summary(manager, "MANAGER").andExpect(status().isForbidden());
    }

    private void assign(Booking booking, Staff specialist) {
        booking.setSpecialist(specialist);
    }

    private ResultActions summary(String username, String role) throws Exception {
        return mvc.perform(get("/specialist/work-summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(username, List.of(role))));
    }
}
