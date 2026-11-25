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
    void ownerSeesCompletedEvidenceButSharedAndPublicJourneysDoNotExposeIt() throws Exception {
        long location = jdbc.queryForObject("SELECT id FROM location WHERE code = 'HUB-HCM'", Long.class);
        long staff = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'manager.dev'", Long.class);
        long horse = jdbc.queryForObject("SELECT min(id) FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        long stay = jdbc.queryForObject("""
                INSERT INTO horse_stay (booking_horse_id, stay_type, location_id, planned_start_at, planned_end_at)
                VALUES (?, 'ASSEMBLY', ?, now(), now() + interval '1 hour') RETURNING id
                """, Long.class, horse, location);
        long handover = jdbc.queryForObject("""
                INSERT INTO handover (handover_type, horse_stay_id, handled_by, status, handed_at)
                VALUES ('PICKUP', ?, ?, 'DONE', now()) RETURNING id
                """, Long.class, stay, staff);
        addHandoverHorse(handover, horse, location);
        jdbc.update("""
                INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, handover_id)
                SELECT id, 'https://example.test/' || code, code, ?, 'UPLOADED', ?
                FROM document_type WHERE code IN ('HANDOVER_RECORD', 'HANDOVER_PHOTO')
                """, staff, handover);

        journey("customer1.dev", "CUSTOMER").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horses[0].handovers[0].evidence.length()").value(2));
        String stranger = testData.customer(testData.unique("tracking.evidence")).getUser().getUsername();
        journey(stranger, "CUSTOMER").andExpect(status().isNotFound());
        track("[\"dev-direct-001\"]", "0001").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].horses[0].evidence").doesNotExist());

        long otherHorse = jdbc.queryForObject("""
                SELECT min(bh.id) FROM booking_horse bh JOIN booking b ON b.id = bh.booking_id
                WHERE b.code = 'DEV-DOMESTIC-001'
                """, Long.class);
        addHandoverHorse(handover, otherHorse, location);
        journey("customer1.dev", "CUSTOMER").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horses[0].handovers[0].evidence.length()").value(0));
    }

    private void addHandoverHorse(long handover, long horse, long location) {
        long leg = jdbc.queryForObject("""
                INSERT INTO horse_leg (booking_horse_id, sequence_number, leg_type, from_location_id, to_location_id,
                                       agent_party_id, planned_start_at, planned_end_at, status)
                VALUES (?, 1, 'PICKUP', ?, ?, (SELECT min(id) FROM party), now(), now() + interval '1 hour', 'COMPLETED')
                RETURNING id
                """, Long.class, horse, location, location);
        jdbc.update("INSERT INTO handover_horse (handover_id, horse_leg_id, is_microchip_matched) VALUES (?, ?, true)", handover, leg);
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
