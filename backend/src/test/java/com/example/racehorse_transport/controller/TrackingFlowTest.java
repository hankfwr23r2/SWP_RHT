package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// B6: ai được xem hành trình và tra cứu công khai. Ca có chặng, giao nhận nằm ở DeliveryFailureFlowTest
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class TrackingFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestData testData;

    private long booking;

    @BeforeEach
    void setUp() {
        booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
    }

    @Test
    void ownerAndStaffSeeTheJourneyOthersDoNot() throws Exception {
        journey("customer1.dev", "CUSTOMER").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingCode").value("DEV-DIRECT-001"))
                .andExpect(jsonPath("$.data.horses[0].holder").value("SHIPPER"))
                .andExpect(jsonPath("$.data.horses[0].legs.length()").value(0));
        journey("manager.dev", "MANAGER").andExpect(status().isOk());

        String stranger = testData.customer(testData.unique("tracking.stranger")).getUser().getUsername();
        journey(stranger, "CUSTOMER").andExpect(status().isNotFound());
        journey(testData.staff(testData.unique("tracking.driver"), "DRIVER").getUser().getUsername(), "DRIVER").andExpect(status().isForbidden());
    }

    @Test
    void publicLookupNeedsTheShipperPhoneAndHidesTheReason() throws Exception {
        track("[\"dev-direct-001\", \"BK-KHONGCO\"]", "0001").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("DEV-DIRECT-001"))
                .andExpect(jsonPath("$.data[0].found").value(true))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[0].horses[0].holder").value("SHIPPER"))
                .andExpect(jsonPath("$.data[0].horses[0].horseName").isNotEmpty())
                .andExpect(jsonPath("$.data[1].found").value(false));
        track("[\"wb-dev-0001\"]", "0001").andExpect(jsonPath("$.data[0].found").value(true));
        // Số người nhận không dùng để tra cứu
        track("[\"DEV-DIRECT-001\"]", "0011").andExpect(jsonPath("$.data[0].found").value(false))
                .andExpect(jsonPath("$.data[0].horses.length()").value(0));
        track("[\"DEV-DIRECT-001\"]", "12a4").andExpect(status().isBadRequest());
    }

    @Test
    void repeatedWrongPhonesLockTheBookingEvenForTheRightPhone() throws Exception {
        for (int i = 0; i < 5; i++) track("[\"DEV-DOMESTIC-001\"]", "9999").andExpect(jsonPath("$.data[0].found").value(false));
        track("[\"DEV-DOMESTIC-001\"]", "0001").andExpect(status().isTooManyRequests());
    }

    private ResultActions journey(String username, String role) throws Exception {
        return mvc.perform(get("/bookings/{id}/journey", booking)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtils.generateToken(username, List.of(role))));
    }

    private ResultActions track(String codes, String phone) throws Exception {
        return mvc.perform(post("/public/tracking").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codes\":%s,\"phoneLast4\":\"%s\"}".formatted(codes, phone)));
    }
}
