package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.RegulationService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.support.TestData;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Demo trên giờ thật: các mốc thời gian chặn thao tác được bỏ qua; giá trị lưu trong DB và trang quy định cho khách giữ nguyên
@PostgresIntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.demo.skip-time-rules=true")
class DemoLiveFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwt;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private SystemConfigService configs;
    @Autowired private RegulationService regulations;
    @Autowired private DepartureService departures;

    @Test
    void waitingAndRestTimesAreZeroButCustomerRulesShowStoredValues() {
        assertThat(configs.timeRulesSkipped()).isTrue();
        assertThat(configs.getInt("MIN_REST_MINUTES")).isZero();
        assertThat(configs.getInt("HANDOVER_FREE_WAIT_MINUTES")).isZero();
        assertThat(configs.getInt("BALANCE_WAIT_MINUTES")).isEqualTo(30);
        assertThat(configs.transportRules().hubCheckinHours()).isEqualTo(3);
    }

    @Test
    void documentsNeedNoLeadTimeAndPickupMayBeOnDepartureDay() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.ofHours(7));
        var resolved = regulations.resolve(BookingType.EXPORT, "VN", "KH", today, null);
        assertThat(resolved.leadDays()).isZero();
        assertThat(resolved.rule().permitLeadDays()).isZero();
        assertThat(BookingSubmissionPolicy.latestPickupDate(BookingType.INTERPROVINCE, today, configs.timeRulesSkipped())).isEqualTo(today);
    }

    @Test
    void managerConfirmsAFarDepartureAndBookingStaysOpenUntilDeparture() throws Exception {
        LocalDate date = LocalDate.ofInstant(clock.instant(), ZoneOffset.ofHours(7)).plusDays(10);
        var planned = date.atTime(6, 0).atOffset(ZoneOffset.ofHours(7));
        long route = jdbc.queryForObject("SELECT id FROM route WHERE code = 'HCM-DN'", Long.class);
        long departure = jdbc.queryForObject("""
                INSERT INTO departure (route_id, departure_date, planned_departure_at, booking_cutoff_at, stall_capacity, status)
                VALUES (?, ?, ?, ?, 9, 'CLOSED') RETURNING id
                """, Long.class, route, date, planned, planned.minusDays(3));

        departures.closePastCutoff();
        assertThat(jdbc.queryForObject("SELECT status FROM departure WHERE id = ?", String.class, departure)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT booking_cutoff_at FROM departure WHERE id = ?", Instant.class, departure))
                .isEqualTo(planned.toInstant().minusSeconds(60));

        String username = data.unique("demo-manager");
        data.staff(username, "MANAGER");
        // Bình thường chỉ xác nhận được trong 48 giờ cuối
        mvc.perform(post("/departures/{id}/confirm-run", departure)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(username, List.of("MANAGER"), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runConfirmedAt").isNotEmpty());
    }
}
