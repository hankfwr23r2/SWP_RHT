package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.DeliveryFailureService;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
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

// B5: quá giờ chờ người nhận → giao thất bại (TIMEOUT, chặng FAILED, sự cố HANDOVER_TIMEOUT) → về hub → giao lại thành công → đơn DELIVERED
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class DeliveryFailureFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;
    @Autowired private DeliveryFailureService deliveryFailures;

    private String coordinator;
    private Staff coordinatorStaff;
    private long booking;
    private long pickup;
    private long delivery;
    private long line;
    private String chip;
    private long hcm;
    private Instant day;

    @BeforeEach
    void setUp() {
        coordinatorStaff = testData.staff(testData.unique("redeliver.coordinator"), "COORDINATOR");
        coordinator = token(coordinatorStaff, "COORDINATOR");
        booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
        pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, booking);
        delivery = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'DELIVERY'", Long.class, booking);
        line = jdbc.queryForObject("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        chip = jdbc.queryForObject("SELECT h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id WHERE bh.id = ?", String.class, line);
        hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        day = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking)
                .toLocalDate().atTime(1, 0).toInstant(ZoneOffset.UTC);
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'LOCAL' WHERE id = ?", booking);
        entityManager.clear();
    }

    @Test
    void failedDeliveryReturnsToHubAndIsRedeliveredUntilTheBookingIsDelivered() throws Exception {
        // Lượt 1: chạy thẳng, tới điểm giao không gặp người nhận
        Trip first = plan("DIRECT", 0, stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60),
                stop("bookingAddressId", delivery, "DELIVERY", 240), stop("locationId", hcm, "HUB", 480));
        first.assign(line, 1, 2);
        first.startAndReceiveAt(1);
        first.act("/trip-stops/" + first.stops.get(1) + "/depart").andExpect(status().isOk());
        first.act("/trip-stops/" + first.stops.get(2) + "/arrive").andExpect(status().isOk());
        long handover = first.openHandover(2, "DELIVERY");

        first.act("/handovers/" + handover + "/fail").andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Chưa bắt đầu chờ người nhận."));
        first.act("/handovers/" + handover + "/start-waiting").andExpect(status().isOk());
        first.act("/handovers/" + handover + "/start-waiting").andExpect(status().isConflict());
        first.act("/handovers/" + handover + "/fail").andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Chưa hết 120 phút chờ miễn phí."));
        entityManager.flush();
        jdbc.update("UPDATE handover SET wait_started_at = now() - interval '121 minutes' WHERE id = ?", handover);
        entityManager.clear();
        first.act("/handovers/" + handover + "/fail").andExpect(status().isOk());

        entityManager.flush();
        long failedLeg = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND sequence_number = 1", Long.class, line);
        assertThat(jdbc.queryForObject("SELECT status FROM handover WHERE id = ?", String.class, handover)).isEqualTo("TIMEOUT");
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, failedLeg)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT cause_type FROM incident WHERE horse_leg_id = ? AND incident_type = 'HANDOVER_TIMEOUT'", String.class, failedLeg))
                .isEqualTo("RECIPIENT_ABSENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_recipient WHERE user_id = ?", Integer.class, coordinatorStaff.getUserId())).isEqualTo(1);
        assertThat(bookingStatus()).isEqualTo("IN_OPERATION");
        // B6: hành trình ghi chặng thất bại, biên bản quá giờ; ngựa vẫn trong tay công ty
        journey().andExpect(jsonPath("$.data.horses[0].holder").value("COMPANY"))
                .andExpect(jsonPath("$.data.horses[0].legs[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.horses[0].handovers[1].handoverType").value("DELIVERY"))
                .andExpect(jsonPath("$.data.horses[0].handovers[1].status").value("TIMEOUT"));

        // Xe đưa ngựa về hub và kết thúc lượt
        first.act("/trip-stops/" + first.stops.get(2) + "/depart").andExpect(status().isOk());
        first.act("/trip-stops/" + first.stops.get(3) + "/arrive").andExpect(status().isOk());
        first.act("/vehicle-trips/" + first.id + "/complete").andExpect(status().isOk());

        // Lượt 2: giao lại từ hub
        Trip second = plan("DELIVERY", 24 * 60, stop("locationId", hcm, "HUB", 24 * 60), stop("bookingAddressId", delivery, "DELIVERY", 24 * 60 + 120),
                stop("locationId", hcm, "HUB", 24 * 60 + 300));
        second.assign(line, 0, 1);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT replaced_horse_leg_id FROM horse_leg WHERE booking_horse_id = ? AND sequence_number = 2", Long.class, line))
                .isEqualTo(failedLeg);
        second.acknowledgeAndStart();
        second.act("/trip-stops/" + second.stops.get(0) + "/arrive").andExpect(status().isOk());
        // Nhận ngựa từ hub rồi mới lên xe
        second.scanAndComplete(second.openHandover(0, "HUB_OUT"));
        second.act("/trip-stops/" + second.stops.get(0) + "/depart").andExpect(status().isOk());
        second.act("/trip-stops/" + second.stops.get(1) + "/arrive").andExpect(status().isOk());
        long redelivery = second.openHandover(1, "DELIVERY");
        second.scanAndComplete(redelivery);
        assertThat(bookingStatus()).isEqualTo("DELIVERED");
        journey().andExpect(jsonPath("$.data.horses[0].holder").value("CONSIGNEE"))
                .andExpect(jsonPath("$.data.horses[0].holderSince").isNotEmpty())
                .andExpect(jsonPath("$.data.horses[0].legs.length()").value(2))
                .andExpect(jsonPath("$.data.horses[0].legs[1].replacedHorseLegId").value(failedLeg))
                .andExpect(jsonPath("$.data.horses[0].legs[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.horses[0].handovers[*].handoverType").value(org.hamcrest.Matchers.contains("PICKUP", "DELIVERY", "HUB_OUT", "DELIVERY")));
    }

    @Test
    void schedulerTimesOutWaitsPastTheFreeWait() throws Exception {
        Trip trip = plan("DIRECT", 0, stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60),
                stop("bookingAddressId", delivery, "DELIVERY", 240), stop("locationId", hcm, "HUB", 480));
        trip.assign(line, 1, 2);
        trip.startAndReceiveAt(1);
        trip.act("/trip-stops/" + trip.stops.get(1) + "/depart").andExpect(status().isOk());
        trip.act("/trip-stops/" + trip.stops.get(2) + "/arrive").andExpect(status().isOk());
        long handover = trip.openHandover(2, "DELIVERY");
        trip.act("/handovers/" + handover + "/start-waiting").andExpect(status().isOk());

        assertThat(deliveryFailures.timeoutExpired()).isZero();
        entityManager.flush();
        jdbc.update("UPDATE handover SET wait_started_at = now() - interval '3 hours' WHERE id = ?", handover);
        entityManager.clear();
        assertThat(deliveryFailures.timeoutExpired()).isEqualTo(1);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM handover WHERE id = ?", String.class, handover)).isEqualTo("TIMEOUT");
    }

    private ResultActions journey() throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mvc.perform(get("/bookings/{id}/journey", booking).header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isOk());
    }

    private String bookingStatus() {
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking);
    }

    private Trip plan(String type, int offsetMinutes, String... stops) throws Exception {
        Staff driver = testData.staff(testData.unique("redeliver.driver"), "DRIVER");
        String body = "{\"tripType\":\"%s\",\"vehicleId\":%d,\"stops\":[%s]}".formatted(type, testData.vehicle(2, 0).getId(), String.join(",", stops));
        String created = mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        List<Long> stopIds = ((List<Number>) JsonPath.read(created, "$.data.stops[*].id")).stream().map(Number::longValue).toList();
        mvc.perform(post("/vehicle-trips/{id}/crew-assignments", id).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"crewRole\":\"DRIVER\"}".formatted(driver.getUserId()))).andExpect(status().isCreated());
        return new Trip(id, stopIds, token(driver, "DRIVER"));
    }

    private final class Trip {
        final long id;
        final List<Long> stops;
        final String driver;

        Trip(long id, List<Long> stops, String driver) {
            this.id = id;
            this.stops = stops;
            this.driver = driver;
        }

        void assign(long bookingHorse, int board, int alight) throws Exception {
            mvc.perform(post("/vehicle-trips/{id}/horse-legs", id).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(bookingHorse, stops.get(board), stops.get(alight))))
                    .andExpect(status().isCreated());
        }

        void acknowledgeAndStart() throws Exception {
            String mine = mvc.perform(get("/crew-assignments/mine").header(HttpHeaders.AUTHORIZATION, driver)).andReturn().getResponse().getContentAsString();
            act("/crew-assignments/" + ((Number) JsonPath.read(mine, "$.data[0].id")).longValue() + "/acknowledge").andExpect(status().isOk());
            act("/vehicle-trips/" + id + "/start").andExpect(status().isOk());
        }

        void startAndReceiveAt(int pickupIndex) throws Exception {
            acknowledgeAndStart();
            for (int i = 0; i < pickupIndex; i++) {
                act("/trip-stops/" + stops.get(i) + "/arrive").andExpect(status().isOk());
                act("/trip-stops/" + stops.get(i) + "/depart").andExpect(status().isOk());
            }
            act("/trip-stops/" + stops.get(pickupIndex) + "/arrive").andExpect(status().isOk());
            scanAndComplete(openHandover(pickupIndex, "PICKUP"));
        }

        long openHandover(int stopIndex, String type) throws Exception {
            String body = mvc.perform(post("/trip-stops/{id}/handovers", stops.get(stopIndex)).header(HttpHeaders.AUTHORIZATION, driver)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"%s\"}".formatted(type)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            List<Number> ids = JsonPath.read(body, "$.data.handovers[*].id");
            return ids.getLast().longValue();
        }

        void scanAndComplete(long handover) throws Exception {
            long leg = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, line, id);
            mvc.perform(post("/handovers/{id}/horses", handover).header(HttpHeaders.AUTHORIZATION, driver).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"horseLegId\":%d,\"microchip\":\"%s\"}".formatted(leg, chip))).andExpect(status().isOk());
            signedRecord(handover);
            act("/handovers/" + handover + "/complete").andExpect(status().isOk());
        }

        ResultActions act(String path) throws Exception {
            return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, driver));
        }
    }

    private String stop(String key, long id, String type, int minutes) {
        String at = day.plusSeconds(60L * minutes).toString();
        String leave = day.plusSeconds(60L * (minutes + 30)).toString();
        return "{\"%s\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}".formatted(key, id, type, at, leave);
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role));
    }

    // Biên bản đã ký (giả lập ảnh đã tải lên): bắt buộc trước khi hoàn tất nhận hoặc giao ngựa
    private void signedRecord(long handoverId) {
        jdbc.update("INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, handover_id) "
                + "SELECT id, 'test://record/' || md5(random()::text), 'record.jpg', (SELECT min(id) FROM app_user), 'UPLOADED', ? FROM document_type WHERE code = 'HANDOVER_RECORD'", handoverId);
    }
}
