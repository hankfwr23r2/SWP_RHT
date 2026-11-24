package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
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

// B2: tổ xe chạy trọn một lượt chạy thẳng của đơn seed DEV-DIRECT-001: xuất phát, đón, quét chip, rời điểm đón, giao, kết thúc
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class TripExecutionFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;
    // Lưu tệp lên Cloudinary là dịch vụ bên ngoài
    @org.springframework.test.context.bean.override.mockito.MockitoBean private com.example.racehorse_transport.client.CloudinaryClient cloudinary;

    private String coordinator;
    private String driver;
    private String escort;
    private long trip;
    private List<Long> stops;
    private long leg;
    private long booking;
    private String chip;

    @BeforeEach
    void planDirectTrip() throws Exception {
        coordinator = token(testData.staff(testData.unique("exec.coordinator"), "COORDINATOR"), "COORDINATOR");
        Staff driverStaff = testData.staff(testData.unique("exec.driver"), "DRIVER");
        Staff escortStaff = testData.staff(testData.unique("exec.escort"), "ESCORT");
        driver = token(driverStaff, "DRIVER");
        escort = token(escortStaff, "ESCORT");

        booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
        long pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, booking);
        long delivery = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'DELIVERY'", Long.class, booking);
        long bookingHorse = jdbc.queryForObject("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        chip = jdbc.queryForObject("SELECT h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id WHERE bh.id = ?", String.class, bookingHorse);
        long hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        Instant start = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking)
                .toLocalDate().atTime(1, 0).toInstant(ZoneOffset.UTC);

        String body = "{\"tripType\":\"DIRECT\",\"vehicleId\":%d,\"stops\":[%s,%s,%s,%s]}".formatted(testData.vehicle(2, 0).getId(),
                stop("locationId", hcm, "HUB", start, 0), stop("bookingAddressId", pickup, "PICKUP", start, 60),
                stop("bookingAddressId", delivery, "DELIVERY", start, 240), stop("locationId", hcm, "HUB", start, 480));
        trip = id(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON).content(body)));
        stops = ((List<Number>) JsonPath.read(content(get("/vehicle-trips/" + trip), coordinator), "$.data.stops[*].id")).stream().map(Number::longValue).toList();
        for (Staff member : List.of(driverStaff, escortStaff)) {
            mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), member == driverStaff ? "DRIVER" : "ESCORT")))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(bookingHorse, stops.get(1), stops.get(2))))
                .andExpect(status().isCreated());
        leg = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND status = 'PLANNED'", Long.class, bookingHorse);
    }

    @Test
    void crewRunsTheWholeDirectTrip() throws Exception {
        // Đơn nội tỉnh không cần giấy bắt buộc (DocumentCompletenessService của luồng A); ca thiếu giấy ở test riêng
        makeLocal();
        // Tài xế chưa xác nhận nhận việc thì chưa xuất phát
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tài xế chưa xác nhận nhận việc."));
        acknowledge(driver);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk()).andExpect(jsonPath("$.data.trip.status").value("IN_PROGRESS"));

        // Đi theo thứ tự điểm dừng
        act(escort, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa rời điểm dừng trước."));
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());

        // Khách mới trả cọc: số dư lập khi nhận ngựa xong (seed có sẵn số dư đã trả nên bỏ đi)
        entityManager.flush();
        jdbc.update("DELETE FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();

        // Giao nhận ở điểm đón: loại sai với điểm dừng bị từ chối
        handover(escort, stops.get(1), "DELIVERY").andExpect(status().isBadRequest());
        long pickup = handoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Còn lần giao nhận chưa xong ở điểm dừng này."));
        scan(escort, pickup, "985000000999999").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].horses[0].microchipMatched").value(false));
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa có con nào quét đúng chip."));
        scan(escort, pickup, chip).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].horses[0].microchipMatched").value(true));
        // MAN_HINH 4.3: chưa có biên bản đã ký thì chưa hoàn tất nhận ngựa
        jdbc.update("DELETE FROM document WHERE handover_id = ?", pickup);
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa có biên bản giao nhận. Chụp biên bản đã ký trước khi hoàn tất."));
        signedRecord(pickup);
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].recordAttached").value(true))
                .andExpect(jsonPath("$.data.handovers[0].status").value("DONE"))
                .andExpect(jsonPath("$.data.horses[0].leg.status").value("IN_PROGRESS"));

        // Nhận xong thì lập khoản số dư = tổng báo giá trừ tiền cọc, hạn tính từ lúc nhận
        var balance = jdbc.queryForMap("""
                SELECT p.amount, p.status, p.due_at, h.handed_at, q.total_amount - q.deposit_amount AS expected
                FROM payment p JOIN quotation q ON q.id = p.quotation_id, handover h
                WHERE p.booking_id = ? AND p.payment_type = 'BALANCE' AND h.id = ?""", booking, pickup);
        assertThat(balance.get("status")).isEqualTo("PENDING");
        assertThat((java.math.BigDecimal) balance.get("amount")).isEqualByComparingTo((java.math.BigDecimal) balance.get("expected"));
        assertThat(((java.sql.Timestamp) balance.get("due_at")).toInstant()).isAfter(((java.sql.Timestamp) balance.get("handed_at")).toInstant());

        // Quy tắc #32: chưa trả đủ thì xe không rời nơi nhận
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Đơn DEV-DIRECT-001 chưa trả đủ")));
        entityManager.flush();
        jdbc.update("UPDATE payment SET status = 'PAID', paid_at = now() WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("IN_OPERATION");

        // Chưa giao xong thì chưa kết thúc lượt
        act(driver, "/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());
        long delivery = handoverId(handover(escort, stops.get(2), "DELIVERY").andExpect(status().isCreated()));
        scan(escort, delivery, chip).andExpect(status().isOk());
        act(escort, "/handovers/" + delivery + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horses[0].leg.status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("COMPLETED");
        assertThat(bookingStatus()).isEqualTo("DELIVERED");

        act(driver, "/trip-stops/" + stops.get(2) + "/depart").andExpect(status().isOk());
        act(driver, "/vehicle-trips/" + trip + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa tới điểm dừng cuối."));
        act(driver, "/trip-stops/" + stops.get(3) + "/arrive").andExpect(status().isOk());
        act(driver, "/vehicle-trips/" + trip + "/complete").andExpect(status().isOk()).andExpect(jsonPath("$.data.trip.status").value("COMPLETED"));
    }

    @Test
    void overdueBalanceReturnsTheHorseAndCancelsTheBooking() throws Exception {
        makeLocal();
        entityManager.flush();
        jdbc.update("DELETE FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();
        acknowledge(driver);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        // Chưa nhận con nào thì không có gì để trả
        handover(escort, stops.get(1), "RETURN").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không có con ngựa nào cần giao nhận loại này ở điểm dừng này."));
        long pickup = handoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));
        scan(escort, pickup, chip).andExpect(status().isOk());
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isOk());

        // Còn trong hạn trả số dư: luồng A từ chối hủy, không có gì thay đổi
        handover(escort, stops.get(1), "RETURN").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn chưa đủ điều kiện hủy do quá thời gian chờ trả số dư."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover WHERE trip_stop_id = ? AND handover_type = 'RETURN'", Long.class, stops.get(1))).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("IN_PROGRESS");

        // Quá hạn: trả ngựa cho người gửi, đơn bị hủy, khoản số dư bị hủy, xe rời đi không cần kiểm thanh toán
        entityManager.flush();
        jdbc.update("UPDATE payment SET due_at = now() - interval '1 minute' WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();
        handover(escort, stops.get(1), "RETURN").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers[1].handoverType").value("RETURN"))
                .andExpect(jsonPath("$.data.handovers[1].status").value("DONE"))
                .andExpect(jsonPath("$.data.handovers[1].horses[0].scannedMicrochip").value(chip));
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("CANCELLED");
        assertThat(bookingStatus()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", String.class, booking))
                .isEqualTo("CANCELLED");
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());
    }

    @Test
    void onlyCrewOfTheTripOperatesIt() throws Exception {
        acknowledge(driver);
        String stranger = token(testData.staff(testData.unique("exec.stranger"), "DRIVER"), "DRIVER");
        mvc.perform(get("/crew/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, stranger)).andExpect(status().isForbidden());
        act(stranger, "/vehicle-trips/" + trip + "/start").andExpect(status().isForbidden());
        act(coordinator, "/vehicle-trips/" + trip + "/start").andExpect(status().isForbidden());
        mvc.perform(get("/crew/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, escort))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.trip.stops.length()").value(4));
    }

    @Test
    void onlyHorsesWithMatchingChipAreReceived() throws Exception {
        long domestic = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DOMESTIC-001'", Long.class);
        long pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, domestic);
        List<Long> lines = jdbc.queryForList("SELECT id FROM booking_horse WHERE booking_id = ? ORDER BY id", Long.class, domestic);
        long hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        Instant start = jdbc.queryForObject("SELECT planned_departure_at FROM departure d JOIN booking b ON b.departure_id = d.id WHERE b.id = ?",
                java.sql.Timestamp.class, domestic).toInstant().minusSeconds(6 * 3600);
        Staff driverStaff = testData.staff(testData.unique("exec.driver2"), "DRIVER");
        String driver2 = token(driverStaff, "DRIVER");
        long pickupTrip = id(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"PICKUP\",\"vehicleId\":%d,\"stops\":[%s,%s,%s]}".formatted(testData.vehicle(2, 0).getId(),
                        stop("locationId", hcm, "HUB", start, 0), stop("bookingAddressId", pickup, "PICKUP", start, 60), stop("locationId", hcm, "HUB", start, 240)))));
        List<Long> pickupStops = ((List<Number>) JsonPath.read(content(get("/vehicle-trips/" + pickupTrip), coordinator), "$.data.stops[*].id"))
                .stream().map(Number::longValue).toList();
        mvc.perform(post("/vehicle-trips/{id}/crew-assignments", pickupTrip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"crewRole\":\"DRIVER\"}".formatted(driverStaff.getUserId()))).andExpect(status().isCreated());
        for (long line : lines) {
            mvc.perform(post("/vehicle-trips/{id}/horse-legs", pickupTrip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, pickupStops.get(1), pickupStops.get(2))))
                    .andExpect(status().isCreated());
        }
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'LOCAL' WHERE id = ?", domestic);
        entityManager.clear();
        acknowledge(driver2);
        act(driver2, "/vehicle-trips/" + pickupTrip + "/start").andExpect(status().isOk());
        act(driver2, "/trip-stops/" + pickupStops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver2, "/trip-stops/" + pickupStops.get(0) + "/depart").andExpect(status().isOk());
        act(driver2, "/trip-stops/" + pickupStops.get(1) + "/arrive").andExpect(status().isOk());
        long handover = handoverId(handover(driver2, pickupStops.get(1), "PICKUP").andExpect(status().isCreated()));

        long first = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, lines.get(0), pickupTrip);
        long second = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, lines.get(1), pickupTrip);
        String firstChip = jdbc.queryForObject("SELECT h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id WHERE bh.id = ?", String.class, lines.get(0));
        mvc.perform(post("/handovers/{id}/horses", handover).header(HttpHeaders.AUTHORIZATION, driver2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horseLegId\":%d,\"microchip\":\"%s\"}".formatted(first, firstChip))).andExpect(status().isOk());
        mvc.perform(post("/handovers/{id}/horses", handover).header(HttpHeaders.AUTHORIZATION, driver2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horseLegId\":%d,\"microchip\":\"985000000999999\"}".formatted(second))).andExpect(status().isOk());

        // Chip sai: không nhận con thứ hai, bắt buộc có lý do
        refuse(driver2, handover, second, " ").andExpect(status().isBadRequest());
        refuse(driver2, handover, second, "Chip không khớp giấy tờ").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].horses.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT status FROM booking_horse WHERE id = ?", String.class, lines.get(1))).isEqualTo("EXCLUDED");
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, second)).isEqualTo("CANCELLED");
        // Luồng A thay báo giá ngay trong cùng giao dịch
        assertThat(jdbc.queryForList("SELECT status FROM quotation WHERE booking_id = ? ORDER BY version", String.class, domestic))
                .containsExactly("SUPERSEDED", "ACCEPTED");

        act(driver2, "/handovers/" + handover + "/complete").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, first)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, second)).isEqualTo("CANCELLED");
    }

    @Test
    void refusingTheOnlyHorseEndsThePickupAndTheVehicleLeaves() throws Exception {
        makeLocal();
        acknowledge(driver);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        long pickup = handoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));

        refuse(escort, pickup, leg, "Ngựa què chân trước").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].status").value("FAILED"));
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("CANCELLED");
        // Không con nào lên xe: không kiểm thanh toán, đơn không sang IN_OPERATION
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void tripDoesNotStartWhileDocumentsAreMissing() throws Exception {
        acknowledge(driver);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn DEV-DIRECT-001 còn thiếu giấy tờ bắt buộc."));
        assertThat(jdbc.queryForObject("SELECT status FROM vehicle_trip WHERE id = ?", String.class, trip)).isEqualTo("PLANNED");
    }

    @Test
    void tripDoesNotStartWhileInsuranceIsNotConfirmed() throws Exception {
        makeLocal();
        acknowledge(driver);
        entityManager.flush();
        jdbc.update("UPDATE horse_insurance SET status = 'DRAFT', confirmed_by = NULL, confirmed_at = NULL WHERE booking_horse_id = (SELECT booking_horse_id FROM horse_leg WHERE id = ?)", leg);
        entityManager.clear();
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Bảo hiểm của")));
    }

    @Test
    void crewUploadsTheSignedRecordAndHandlesOriginalPapers() throws Exception {
        makeLocal();
        // Hộ chiếu ngựa đã duyệt: giấy gốc khách giao lúc nhận ngựa, trả lại lúc giao
        long passport = jdbc.queryForObject("""
                INSERT INTO document (document_type_id, document_requirement_id, file_url, file_name, uploaded_by, status, reviewed_by, reviewed_at, horse_id)
                SELECT dt.id, (SELECT dr.id FROM document_requirement dr WHERE dr.document_type_id = dt.id ORDER BY dr.id LIMIT 1),
                       'test://passport/' || md5(random()::text), 'passport.pdf', u.id, 'APPROVED', u.id, now(),
                       (SELECT horse_id FROM booking_horse WHERE booking_id = ?)
                FROM document_type dt, (SELECT min(id) AS id FROM app_user) u WHERE dt.code = 'HORSE_PASSPORT' RETURNING id""", Long.class, booking);
        org.mockito.Mockito.when(cloudinary.uploadFile(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> "https://cdn.example/record-" + System.nanoTime() + ".jpg");
        acknowledge(driver);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        long pickup = rawHandoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));
        scan(escort, pickup, chip).andExpect(status().isOk());

        mvc.perform(get("/handovers/{id}/papers", pickup).header(HttpHeaders.AUTHORIZATION, escort)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].documentId").value(passport)).andExpect(jsonPath("$.data[0].direction").doesNotExist());
        paper(pickup, passport).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].direction").value("RECEIVED"));
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isConflict());
        uploadRecord(pickup).andExpect(status().isOk()).andExpect(jsonPath("$.data.handovers[0].recordAttached").value(true));
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());

        act(driver, "/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());
        long delivery = rawHandoverId(handover(escort, stops.get(2), "DELIVERY").andExpect(status().isCreated()));
        scan(escort, delivery, chip).andExpect(status().isOk());
        paper(delivery, passport).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].direction").value("RETURNED"));
        uploadRecord(delivery).andExpect(status().isOk());
        act(escort, "/handovers/" + delivery + "/complete").andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT direction FROM handover_document WHERE document_id = ? ORDER BY handover_id", String.class, passport))
                .containsExactly("RECEIVED", "RETURNED");
        // Người ngoài tổ lái không tải biên bản được
        String stranger = token(testData.staff(testData.unique("exec.paper.stranger"), "ESCORT"), "ESCORT");
        mvc.perform(get("/handovers/{id}/papers", delivery).header(HttpHeaders.AUTHORIZATION, stranger)).andExpect(status().isForbidden());
    }

    private ResultActions paper(long handoverId, long documentId) throws Exception {
        return mvc.perform(post("/handovers/{id}/papers", handoverId).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"documentId\":%d}".formatted(documentId)));
    }

    private ResultActions uploadRecord(long handoverId) throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "bien-ban.jpg", "image/jpeg", new byte[] {1, 2, 3});
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/handovers/{id}/record", handoverId)
                .file(file).header(HttpHeaders.AUTHORIZATION, escort));
    }

    // Lần giao nhận vừa mở, chưa có biên bản
    private static long rawHandoverId(ResultActions result) throws Exception {
        List<Number> ids = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.handovers[*].id");
        return ids.getLast().longValue();
    }

    // Sửa thẳng DB rồi bỏ bộ đệm của JPA để service đọc lại
    private void makeLocal() {
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'LOCAL' WHERE id = ?", booking);
        entityManager.clear();
    }

    private void acknowledge(String token) throws Exception {
        long assignment = ((Number) JsonPath.read(content(get("/crew-assignments/mine"), token), "$.data[0].id")).longValue();
        act(token, "/crew-assignments/" + assignment + "/acknowledge").andExpect(status().isOk());
    }

    private String bookingStatus() {
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking);
    }

    private ResultActions act(String token, String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions handover(String token, long stopId, String type) throws Exception {
        return mvc.perform(post("/trip-stops/{id}/handovers", stopId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"%s\"}".formatted(type)));
    }

    private ResultActions scan(String token, long handoverId, String microchip) throws Exception {
        return mvc.perform(post("/handovers/{id}/horses", handoverId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"horseLegId\":%d,\"microchip\":\"%s\"}".formatted(leg, microchip)));
    }

    private ResultActions refuse(String token, long handoverId, long horseLegId, String reason) throws Exception {
        return mvc.perform(post("/handovers/{id}/refusals", handoverId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"horseLegId\":%d,\"reason\":\"%s\"}".formatted(horseLegId, reason)));
    }

    private String content(org.springframework.test.web.servlet.RequestBuilder request, String token) throws Exception {
        var builder = (org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) request;
        return mvc.perform(builder.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // Lần giao nhận vừa mở, kèm biên bản đã ký để các bước sau hoàn tất được (ca thiếu biên bản kiểm riêng)
    private long handoverId(ResultActions result) throws Exception {
        List<Number> ids = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.handovers[*].id");
        signedRecord(ids.getLast().longValue());
        return ids.getLast().longValue();
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private static String stop(String key, long id, String type, Instant base, int minutes) {
        String at = base.plusSeconds(60L * minutes).toString();
        String leave = base.plusSeconds(60L * (minutes + 30)).toString();
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
