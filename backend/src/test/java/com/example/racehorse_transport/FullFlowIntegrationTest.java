package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.support.HandoverEvidenceFixtures;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Mốc M3 (docs/KE_HOACH_TRIEN_KHAI.md mục 8): chạy trọn 3 ví dụ của docs/NGHIEP_VU_THEO_59_BANG.md mục 6.
// Mỗi ca gồm 3 đoạn, cùng một file theo kế hoạch:
//   1. Thương mại tới CONFIRMED (luồng A): đặt đơn, thẩm định giấy và bảo hiểm, báo giá, cọc, quản lý xác nhận chạy chuyến.
//      CHƯA VIẾT. Tạm thời confirmedBooking() lấy đơn CONFIRMED của dữ liệu mẫu (R__dev_seed_bookings.sql),
//      ghi giấy bắt buộc đã duyệt và xác nhận chạy chuyến bằng SQL. Luồng A thay hàm này bằng các bước thật qua API.
//   2. Vận hành tới DELIVERED (luồng B): điều độ, đón, gửi hub, chặng trục, giao hoặc bàn giao đại lý. Đi qua API như tổ xe.
//   3. Sau khi giao (luồng A): quyết toán, đóng đơn, đánh giá. CHƯA VIẾT, thêm vào cuối mỗi ca.
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class FullFlowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private BookingRepository bookings;
    @Autowired private DocumentCompletenessService documents;
    @Autowired private EntityManager entityManager;
    // Lưu tệp lên Cloudinary là dịch vụ bên ngoài
    @MockitoBean private CloudinaryClient cloudinary;

    private String coordinator;
    private long hcm;
    private long danang;

    @BeforeEach
    void setUp() {
        coordinator = token(testData.staff(testData.unique("m3.coordinator"), "COORDINATOR"), "COORDINATOR");
        hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        danang = locations.findByCode("HUB-DN").orElseThrow().getId();
        when(cloudinary.uploadFile(any(), anyString())).thenReturn("https://example.test/proof.pdf");
    }

    // Đồng Nai đi TP.HCM: cùng hub HUB-HCM nên một chặng DIRECT, không có chuyến
    @Test
    void directBookingIsPickedUpAndDeliveredByOneTrip() throws Exception {
        Case order = confirmedBooking("DEV-DIRECT-001");

        Instant day = order.pickupDate().atTime(1, 0).toInstant(ZoneOffset.UTC);
        Trip trip = plan("DIRECT", testData.vehicle(2, 0).getId(), stop(hcm, "HUB", day, 0), address(order.pickup(), "PICKUP", day, 60),
                address(order.delivery(), "DELIVERY", day, 240), stop(hcm, "HUB", day, 480));
        trip.assign(order, 1, 2);

        trip.start();
        trip.pass(0);
        trip.arrive(1);
        trip.handOver(1, "PICKUP", order);
        trip.depart(1);
        assertThat(bookingStatus(order)).isEqualTo("IN_OPERATION");
        trip.arrive(2);
        trip.handOver(2, "DELIVERY", order);
        trip.depart(2);
        trip.arrive(3);
        trip.complete();

        assertThat(bookingStatus(order)).isEqualTo("DELIVERED");
        journey(order).andExpect(jsonPath("$.data.horses[0].holder").value("CONSIGNEE"))
                .andExpect(jsonPath("$.data.horses[0].legs[*].legType").value(org.hamcrest.Matchers.contains("DIRECT")))
                .andExpect(jsonPath("$.data.horses[0].handovers[*].handoverType").value(org.hamcrest.Matchers.contains("PICKUP", "DELIVERY")));
    }

    // Đồng Nai đi Huế: HUB-HCM khác HUB-DN nên đi trục. Đón về HUB-HCM, chặng trục HCM-DN, xe trục giao tiếp tới Huế
    @Test
    void interprovinceBookingRidesPickupLinehaulAndDeliveryThroughTwoHubs() throws Exception {
        Case order = confirmedBooking("DEV-DOMESTIC-001");
        assertThat(order.horses()).hasSize(2);

        Instant pickupStart = order.departureAt().minusSeconds(10 * 3600);
        Trip pickup = plan("PICKUP", testData.vehicle(4, 0).getId(), stop(hcm, "HUB", pickupStart, 0),
                address(order.pickup(), "PICKUP", pickupStart, 90), stop(hcm, "HUB", pickupStart, 240));
        pickup.assign(order, 1, 2);
        long linehaulVehicle = testData.vehicle(4, 1).getId();
        Trip linehaul = linehaul(linehaulVehicle, order.departureId());
        linehaul.assign(order, 0, linehaul.lastStop());
        // Tổ của xe trục đang ở Đà Nẵng nên chạy tiếp lượt giao, sau một đêm nghỉ ở HUB-DN (giới hạn 10 giờ lái mỗi ngày): 7 giờ sáng hôm sau
        Instant nextMorning = linehaul.plannedEndAt().truncatedTo(java.time.temporal.ChronoUnit.DAYS).plus(java.time.Duration.ofDays(1));
        Trip delivery = plan("DELIVERY", linehaulVehicle, linehaul.driverStaff, linehaul.escortStaff, stop(danang, "HUB", nextMorning, 0),
                address(order.delivery(), "DELIVERY", nextMorning, 90), stop(danang, "HUB", nextMorning, 270));
        delivery.assign(order, 0, 1);

        // Đón ở Đồng Nai, gửi hub HUB-HCM qua đêm chờ chuyến
        pickup.start();
        pickup.pass(0);
        pickup.arrive(1);
        pickup.handOver(1, "PICKUP", order);
        pickup.depart(1);
        pickup.arrive(2);
        pickup.handOver(2, "HUB_IN", order);
        pickup.complete();
        assertThat(bookingStatus(order)).isEqualTo("IN_OPERATION");
        assertThat(openStays(order, "ASSEMBLY", hcm)).isEqualTo(2);

        // Chặng trục: nhận ở hub, ghi uống ăn ở từng trạm nghỉ, giao vào HUB-DN
        linehaul.runLinehaul(order, "HUB_IN");
        assertThat(openStays(order, "ASSEMBLY", hcm)).isZero();
        assertThat(departureStatus(order)).isEqualTo("COMPLETED");

        // Cùng xe trục giao tới Huế
        delivery.start();
        delivery.arrive(0);
        delivery.handOver(0, "HUB_OUT", order);
        delivery.depart(0);
        delivery.arrive(1);
        delivery.handOver(1, "DELIVERY", order);
        delivery.depart(1);
        delivery.arrive(2);
        delivery.complete();

        assertThat(bookingStatus(order)).isEqualTo("DELIVERED");
        for (int horse = 0; horse < 2; horse++) {
            journey(order).andExpect(jsonPath("$.data.horses[%d].holder".formatted(horse)).value("CONSIGNEE"))
                    .andExpect(jsonPath("$.data.horses[%d].legs[*].legType".formatted(horse))
                            .value(org.hamcrest.Matchers.contains("PICKUP", "LINEHAUL", "DELIVERY")))
                    .andExpect(jsonPath("$.data.horses[%d].legs[*].status".formatted(horse))
                            .value(org.hamcrest.Matchers.contains("COMPLETED", "COMPLETED", "COMPLETED")))
                    .andExpect(jsonPath("$.data.horses[%d].handovers[*].handoverType".formatted(horse)).value(
                            org.hamcrest.Matchers.contains("PICKUP", "HUB_IN", "HUB_OUT", "HUB_IN", "HUB_OUT", "DELIVERY")));
        }
    }

    // TP.HCM đi Phnom Penh: đón về HUB-HCM, chặng trục HCM-PNH tới cửa khẩu Mộc Bài, bàn giao đại lý Campuchia, đại lý giao
    @Test
    void exportBookingIsHandedToTheAgentAtTheBorderAndTheAgentDelivers() throws Exception {
        Case order = confirmedBooking("DEV-EXPORT-001");

        Instant pickupStart = order.departureAt().minusSeconds(10 * 3600);
        Trip pickup = plan("PICKUP", testData.vehicle(4, 0).getId(), stop(hcm, "HUB", pickupStart, 0),
                address(order.pickup(), "PICKUP", pickupStart, 90), stop(hcm, "HUB", pickupStart, 240));
        pickup.assign(order, 1, 2);
        Trip linehaul = linehaul(testData.vehicle(4, 0).getId(), order.departureId());
        linehaul.assign(order, 0, linehaul.lastStop());
        long agent = jdbc.queryForObject("SELECT id FROM party WHERE display_name = 'Đại lý vận tải Campuchia (mẫu)'", Long.class);
        Instant atGate = linehaul.plannedEndAt();
        long agentLeg = id(mvc.perform(post("/booking-horses/{id}/agent-legs", order.horses().getFirst().id())
                        .header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentPartyId\":%d,\"gateLocationId\":null,\"plannedStartAt\":\"%s\",\"plannedEndAt\":\"%s\"}"
                                .formatted(agent, atGate, atGate.plusSeconds(86_400))))
                .andExpect(status().isCreated()));

        pickup.start();
        pickup.pass(0);
        pickup.arrive(1);
        pickup.handOver(1, "PICKUP", order);
        pickup.depart(1);
        pickup.arrive(2);
        pickup.handOver(2, "HUB_IN", order);
        pickup.complete();

        // Tới cửa khẩu thì bàn giao cho đại lý; chặng của đại lý bắt đầu
        linehaul.runLinehaul(order, "AGENT");
        assertThat(legStatus(agentLeg)).isEqualTo("IN_PROGRESS");
        assertThat(customerNotices(order)).contains("Đã qua cửa khẩu");
        assertThat(departureStatus(order)).isEqualTo("COMPLETED");

        // Điều phối nhập hộ mốc đại lý đã giao, kèm biên bản của đại lý
        mvc.perform(multipart("/agent-legs/{id}/complete", agentLeg)
                        .file(new MockMultipartFile("proof", "proof.pdf", "application/pdf", new byte[]{1}))
                        .header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk());

        assertThat(bookingStatus(order)).isEqualTo("DELIVERED");
        journey(order).andExpect(jsonPath("$.data.horses[0].legs[*].legType")
                        .value(org.hamcrest.Matchers.contains("PICKUP", "LINEHAUL", "DELIVERY")))
                .andExpect(jsonPath("$.data.horses[0].legs[*].status")
                        .value(org.hamcrest.Matchers.contains("COMPLETED", "COMPLETED", "COMPLETED")))
                .andExpect(jsonPath("$.data.horses[0].handovers[*].handoverType")
                        .value(org.hamcrest.Matchers.contains("PICKUP", "HUB_IN", "HUB_OUT", "AGENT")));
    }

    // ===================== Đoạn 1: thay thế tạm cho luồng A =====================

    private record Line(long id, String chip) {}

    private record Case(long id, LocalDate pickupDate, Long departureId, Instant departureAt, long pickup, long delivery, List<Line> horses) {}

    // Đơn đã qua đoạn thương mại: CONFIRMED, đã cọc (dữ liệu mẫu có sẵn cọc và số dư đã trả), bảo hiểm đã xác nhận,
    // giấy bắt buộc đã duyệt, quản lý đã xác nhận chạy chuyến. Luồng A thay bằng đặt đơn, thẩm định, báo giá, thanh toán qua API.
    private Case confirmedBooking(String code) {
        var row = jdbc.queryForMap("""
                SELECT b.id, b.status, b.requested_pickup_date, b.departure_id, d.planned_departure_at
                FROM booking b LEFT JOIN departure d ON d.id = b.departure_id WHERE b.code = ?""", code);
        long id = ((Number) row.get("id")).longValue();
        assertThat(row.get("status")).isEqualTo("CONFIRMED");
        approveRequiredDocuments(id);
        Long departure = row.get("departure_id") == null ? null : ((Number) row.get("departure_id")).longValue();
        if (departure != null) {
            // Quản lý xác nhận chạy (POST /departures/{id}/confirm-run) chỉ làm được trong mấy giờ trước giờ xuất phát
            jdbc.update("UPDATE departure SET run_confirmed_at = now(), run_confirmed_by = (SELECT min(id) FROM app_user) WHERE id = ?", departure);
        }
        entityManager.flush();
        entityManager.clear();
        List<Line> horses = jdbc.query("""
                SELECT bh.id, h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id
                WHERE bh.booking_id = ? AND bh.status = 'ACTIVE' ORDER BY bh.id""",
                (rs, n) -> new Line(rs.getLong(1), rs.getString(2)), id);
        return new Case(id, ((java.sql.Date) row.get("requested_pickup_date")).toLocalDate(), departure,
                row.get("planned_departure_at") == null ? null : ((Timestamp) row.get("planned_departure_at")).toInstant(),
                address(id, "PICKUP"), address(id, "DELIVERY"), horses);
    }

    // Chuyên viên đã duyệt đủ giấy bắt buộc của đơn (A6), theo đúng quy định áp cho đơn
    private void approveRequiredDocuments(long bookingId) {
        var booking = bookings.findById(bookingId).orElseThrow();
        List<Long> lines = jdbc.queryForList("SELECT id FROM booking_horse WHERE booking_id = ? AND status = 'ACTIVE'", Long.class, bookingId);
        for (DocumentRequirementResponseDto requirement : documents.resolve(booking).requirements()) {
            if (!Boolean.TRUE.equals(requirement.mandatory())) continue;
            switch (requirement.ownerType()) {
                case BOOKING -> insertApproved(requirement, "booking_id", bookingId);
                case HORSE -> lines.forEach(line -> insertApproved(requirement, "horse_id",
                        jdbc.queryForObject("SELECT horse_id FROM booking_horse WHERE id = ?", Long.class, line)));
                case BOOKING_HORSE -> lines.forEach(line -> insertApproved(requirement, "booking_horse_id", line));
                default -> throw new IllegalStateException("Chưa hỗ trợ giấy của " + requirement.ownerType());
            }
        }
        entityManager.flush();
        entityManager.clear();
        assertThat(documents.isComplete(bookingId)).as("đơn đủ giấy bắt buộc").isTrue();
    }

    private void insertApproved(DocumentRequirementResponseDto requirement, String ownerColumn, long ownerId) {
        jdbc.update("""
                INSERT INTO document (document_type_id, document_requirement_id, file_url, file_name, uploaded_by, status, reviewed_by, reviewed_at, %s)
                VALUES (?, ?, 'https://example.test/papers/' || md5(random()::text) || '.pdf', 'giay.pdf',
                        (SELECT min(id) FROM app_user), 'APPROVED', (SELECT min(id) FROM app_user), now(), ?)
                """.formatted(ownerColumn), requirement.documentTypeId(), requirement.id(), ownerId);
    }

    // ===================== Đoạn 2: vận hành (luồng B) =====================

    private Trip plan(String type, long vehicleId, String... stops) throws Exception {
        return plan(type, vehicleId, testData.staff(testData.unique("m3.driver"), "DRIVER"), testData.staff(testData.unique("m3.escort"), "ESCORT"), stops);
    }

    private Trip plan(String type, long vehicleId, Staff driver, Staff escort, String... stops) throws Exception {
        return crewed(driver, escort, id(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"%s\",\"vehicleId\":%d,\"stops\":[%s]}".formatted(type, vehicleId, String.join(",", stops))))
                .andExpect(status().isCreated())));
    }

    // Lượt trục của chuyến: điểm dừng lấy theo tuyến
    private Trip linehaul(long vehicleId, long departureId) throws Exception {
        return crewed(testData.staff(testData.unique("m3.driver"), "DRIVER"), testData.staff(testData.unique("m3.escort"), "ESCORT"),
                id(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"LINEHAUL\",\"vehicleId\":%d,\"departureId\":%d}".formatted(vehicleId, departureId)))
                .andExpect(status().isCreated())));
    }

    // Mỗi lượt một tài xế và một hộ tống
    private Trip crewed(Staff driver, Staff escort, long tripId) throws Exception {
        for (Staff member : List.of(driver, escort)) {
            mvc.perform(post("/vehicle-trips/{id}/crew-assignments", tripId).header(HttpHeaders.AUTHORIZATION, coordinator)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), member == driver ? "DRIVER" : "ESCORT")))
                    .andExpect(status().isCreated());
        }
        String body = read(get("/vehicle-trips/" + tripId));
        List<Long> stops = ((List<Number>) JsonPath.read(body, "$.data.stops[*].id")).stream().map(Number::longValue).toList();
        List<String> types = JsonPath.read(body, "$.data.stops[*].stopType");
        return new Trip(tripId, stops, types, driver, escort);
    }

    private final class Trip {
        final long id;
        final List<Long> stops;
        final List<String> stopTypes;
        final Staff driverStaff;
        final Staff escortStaff;
        final String driver;
        final String escort;

        Trip(long id, List<Long> stops, List<String> stopTypes, Staff driverStaff, Staff escortStaff) {
            this.id = id;
            this.stops = stops;
            this.stopTypes = stopTypes;
            this.driverStaff = driverStaff;
            this.escortStaff = escortStaff;
            this.driver = token(driverStaff, "DRIVER");
            this.escort = token(escortStaff, "ESCORT");
        }

        int lastStop() {
            return stops.size() - 1;
        }

        Instant plannedEndAt() throws Exception {
            return Instant.parse(JsonPath.read(read(get("/vehicle-trips/" + id)), "$.data.plannedEndAt"));
        }

        // Điều phối xếp mọi con của đơn lên lượt xe
        void assign(Case order, int board, int alight) throws Exception {
            for (Line horse : order.horses()) {
                mvc.perform(post("/vehicle-trips/{id}/horse-legs", id).header(HttpHeaders.AUTHORIZATION, coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}"
                                .formatted(horse.id(), stops.get(board), stops.get(alight))))
                        .andExpect(status().isCreated());
            }
        }

        // Tài xế và hộ tống xác nhận nhận việc trước khi xuất phát
        void start() throws Exception {
            for (String member : List.of(driver, escort)) {
                String mine = mvc.perform(get("/crew-assignments/mine").header(HttpHeaders.AUTHORIZATION, member))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                long assignment = ((Number) ((List<?>) JsonPath.read(mine, "$.data[?(@.vehicleTripId == %d)].id".formatted(id))).getFirst()).longValue();
                act(member, "/crew-assignments/" + assignment + "/acknowledge").andExpect(status().isOk());
            }
            act(driver, "/vehicle-trips/" + id + "/start").andExpect(status().isOk());
        }

        void arrive(int stop) throws Exception {
            act(driver, "/trip-stops/" + stops.get(stop) + "/arrive").andExpect(status().isOk());
        }

        void depart(int stop) throws Exception {
            act(driver, "/trip-stops/" + stops.get(stop) + "/depart").andExpect(status().isOk());
        }

        void pass(int stop) throws Exception {
            arrive(stop);
            depart(stop);
        }

        // Hộ tống mở lần giao nhận, quét chip từng con, chụp biên bản đã ký rồi hoàn tất
        void handOver(int stop, String type, Case order) throws Exception {
            String body = mvc.perform(post("/trip-stops/{id}/handovers", stops.get(stop)).header(HttpHeaders.AUTHORIZATION, escort)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"%s\"}".formatted(type)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long handover = ((List<Number>) JsonPath.read(body, "$.data.handovers[*].id")).getLast().longValue();
            for (Line horse : order.horses()) {
                long leg = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, horse.id(), id);
                mvc.perform(post("/handovers/{id}/horses", handover).header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"horseLegId\":%d,\"conditionNote\":\"Khỏe\",\"microchip\":\"%s\"}".formatted(leg, horse.chip())))
                        .andExpect(status().isOk());
            }
            signedRecord(handover);
            act(escort, "/handovers/" + handover + "/complete").andExpect(status().isOk());
        }

        // Quy tắc #34: ở trạm nghỉ ghi uống và ăn cho từng con trên xe; trong test rời trạm sớm nên ghi lý do
        void rest(int stop, Case order) throws Exception {
            arrive(stop);
            for (Line horse : order.horses()) {
                mvc.perform(post("/health-logs").header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bookingHorseId\":%d,\"tripStopId\":%d,\"healthStatus\":\"NORMAL\",\"waterIntake\":\"NORMAL\",\"feedIntake\":\"NORMAL\"}"
                                        .formatted(horse.id(), stops.get(stop))))
                        .andExpect(status().isCreated());
            }
            mvc.perform(post("/trip-stops/{id}/depart", stops.get(stop)).header(HttpHeaders.AUTHORIZATION, driver)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"earlyReason\":\"Chạy thử toàn luồng\"}"))
                    .andExpect(status().isOk());
        }

        // Nhận ngựa ở hub đầu tuyến, đi qua các trạm, giao ở điểm cuối tuyến (vào hub hoặc cho đại lý), kết thúc lượt
        void runLinehaul(Case order, String handoverAtEnd) throws Exception {
            start();
            arrive(0);
            handOver(0, "HUB_OUT", order);
            depart(0);
            for (int stop = 1; stop < lastStop(); stop++) {
                if (stopTypes.get(stop).equals("REST") || stopTypes.get(stop).equals("VITAL_CHECK")) {
                    rest(stop, order);
                } else {
                    pass(stop);
                }
            }
            arrive(lastStop());
            handOver(lastStop(), handoverAtEnd, order);
            complete();
        }

        void complete() throws Exception {
            act(driver, "/vehicle-trips/" + id + "/complete").andExpect(status().isOk());
        }
    }

    // ===================== Tiện ích =====================

    private long address(long bookingId, String type) {
        return jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = ?", Long.class, bookingId, type);
    }

    private static String stop(long locationId, String type, Instant base, int minutes) {
        String at = base.plusSeconds(60L * minutes).toString();
        return "{\"locationId\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}".formatted(locationId, type, at, at);
    }

    private static String address(long addressId, String type, Instant base, int minutes) {
        String at = base.plusSeconds(60L * minutes).toString();
        String leave = base.plusSeconds(60L * (minutes + 30)).toString();
        return "{\"bookingAddressId\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}".formatted(addressId, type, at, leave);
    }

    // Biên bản đã ký (giả lập ảnh đã tải lên); ảnh từng con, giấy gốc và xác nhận của người nhận do HandoverEvidenceFixtures ghi
    private void signedRecord(long handoverId) {
        jdbc.update("INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, handover_id) "
                + "SELECT id, 'test://record/' || md5(random()::text), 'record.jpg', (SELECT min(id) FROM app_user), 'UPLOADED', ? FROM document_type WHERE code = 'HANDOVER_RECORD'", handoverId);
    }

    private ResultActions act(String token, String path) throws Exception {
        return mvc.perform(HandoverEvidenceFixtures.complete(mvc, jdbc, jwtUtils, path).header(HttpHeaders.AUTHORIZATION, token));
    }

    private String read(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mvc.perform(((org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) request)
                        .header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions journey(Case order) throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mvc.perform(get("/bookings/{id}/journey", order.id()).header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isOk());
    }

    private String bookingStatus(Case order) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, order.id());
    }

    private String departureStatus(Case order) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM departure WHERE id = ?", String.class, order.departureId());
    }

    private String legStatus(long legId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, legId);
    }

    private int openStays(Case order, String type, long locationId) {
        entityManager.flush();
        return jdbc.queryForObject("""
                SELECT count(*) FROM horse_stay s JOIN booking_horse bh ON bh.id = s.booking_horse_id
                WHERE bh.booking_id = ? AND s.stay_type = ? AND s.location_id = ? AND s.actual_end_at IS NULL""",
                Integer.class, order.id(), type, locationId);
    }

    // Tiêu đề các thông báo khách nhận được trên đơn
    private List<String> customerNotices(Case order) {
        entityManager.flush();
        return jdbc.queryForList("SELECT n.title FROM notification n JOIN notification_recipient r ON r.notification_id = n.id "
                + "JOIN booking b ON b.id = n.booking_id WHERE n.booking_id = ? AND r.user_id = b.customer_id ORDER BY n.id", String.class, order.id());
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role));
    }
}
