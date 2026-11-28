package com.example.racehorse_transport.controller;
import static com.example.racehorse_transport.controller.BenXeJourneyOperations.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.controller.BenXeJourneyOperations.Crew;
import com.example.racehorse_transport.controller.BenXeJourneyOperations.Horse;
import com.example.racehorse_transport.controller.BenXeJourneyOperations.Stop;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.policy.QuotationPolicy;
import com.example.racehorse_transport.security.TurnstileVerifier;
import com.example.racehorse_transport.service.PricingRouteService;
import com.example.racehorse_transport.support.TestData;
import com.example.racehorse_transport.TestcontainersConfiguration;
import com.example.racehorse_transport.util.VnpayUtil;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;
@SpringBootTest
@ActiveProfiles("test")
@Import({TestData.class, BenXeJourneyFlowTest.Database.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {"vnpay.tmn-code=FLOWTEST", "vnpay.hash-secret=flow-test-signature",
        "spring.test.mockmvc.print=none"})
class BenXeJourneyFlowTest {
    static class Database implements ImportSelector, EnvironmentAware {
        private Environment environment;
        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }
        @Override
        public String[] selectImports(AnnotationMetadata metadata) {
            return environment.getProperty("m9.local-postgres", Boolean.class, false)
                    ? new String[0] : new String[] {TestcontainersConfiguration.class.getName()};
        }
    }

    private static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.now());
    @TestBean(methodName = "journeyClock")
    private Clock clock;
    static Clock journeyClock() {
        return new Clock() {
            @Override public ZoneId getZone() { return QuotationPolicy.BUSINESS_ZONE; }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
            @Override public Instant instant() { return NOW.get(); }
        };
    }
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData data;
    @MockitoBean private TurnstileVerifier turnstile;
    @MockitoBean private CloudinaryClient cloudinary;
    @MockitoBean private PricingRouteService maps;
    private String customer;
    private String manager;
    private String specialist;
    private String username;
    private Staff specialistStaff;
    @BeforeEach
    void setUp() throws Exception {
        NOW.set(Instant.now());
        username = data.unique("m9.customer");
        data.customer(username);
        customer = login(username, "customer");
        manager = login(staff("MANAGER", "HUB-HCM").getUser().getUsername(), "staff");
        specialistStaff = staff("SPECIALIST", "HUB-HCM");
        specialist = login(specialistStaff.getUser().getUsername(), "staff");
        when(cloudinary.uploadFile(any(), anyString()))
                .thenAnswer(call -> "https://example.test/" + java.util.UUID.randomUUID() + ".pdf");
        // Báo giá gọi routeOrEstimate: chuyển sang route đã giả lập ở từng test
        when(maps.routeOrEstimate(any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> maps.route(
                invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
        when(maps.route(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PricingRouteService.RoadEstimate(new BigDecimal("45"), 3600));
    }

    @Test
    void interprovinceFromHoChiMinhCityToHue() throws Exception {
        journey(new Scenario("INTERPROVINCE", "HCM-DN", "HUB-HCM", "HUB-DN",
                new Place("VN-79", "VN", 10.80, 106.74), new Place("VN-46", "VN", 16.46, 107.60),
                2, true, false, false, 49));
    }

    @Test
    void exportViaMocBaiToPhnomPenh() throws Exception {
        journey(new Scenario("EXPORT", "HCM-PNH", "HUB-HCM", "HUB-PNH",
                new Place("VN-79", "VN", 10.80, 106.74), new Place("KH-12", "KH", 11.56, 104.86),
                1, false, true, false, 56));
    }

    @Test
    void importFromVientianeViaCauTreoToNgheAn() throws Exception {
        journey(new Scenario("IMPORT", "VTE-VINH", "HUB-VTE", "HUB-VINH",
                new Place("LA-VT", "LA", 17.99, 102.60), new Place("VN-40", "VN", 18.72, 105.64),
                1, false, false, false, 63));
    }

    @Test
    void privateTruckFromHoChiMinhCityToDaNang() throws Exception {
        journey(new Scenario("INTERPROVINCE", "HCM-DN", "HUB-HCM", "HUB-DN",
                new Place("VN-79", "VN", 10.80, 106.74), new Place("VN-48", "VN", 16.07, 108.15),
                2, false, false, true, 70));
    }

    private record Place(String province, String country, double latitude, double longitude) {
        String address(String role) {
            return """
                    {"addressType":"%s","addressLine":"Điểm M9","provinceCode":"%s","countryCode":"%s",
                     "latitude":%s,"longitude":%s,"contactName":"Người liên hệ","contactPhone":"0901234567"}
                    """.formatted(role, province, country, latitude, longitude);
        }
    }

    private record Scenario(String type, String route, String origin, String destination,
                            Place pickup, Place delivery, int horses, boolean wideWithVet,
                            boolean selfPickup, boolean privateTruck, int leadDays) {}
    private void journey(Scenario scenario) throws Exception {
        LocalDate day = LocalDate.now(clock).plusDays(scenario.leadDays());
        long route = jdbc.queryForObject("SELECT id FROM route WHERE code = ?", Long.class, scenario.route());
        long vehicleClass = jdbc.queryForObject("SELECT id FROM vehicle_class WHERE code = ?", Long.class,
                scenario.origin().equals("HUB-VTE") ? "MEDIUM" : "HEAVY");
        Instant departureAt = day.atTime(6, 0).atZone(QuotationPolicy.BUSINESS_ZONE).toInstant();
        long departure = departureWithBaseVehicle(route, day, departureAt, vehicleClass);
        long booking = confirmBooking(scenario, departure, day.minusDays(1));
        if (scenario.privateTruck()) {
            // Đơn ghép cùng chuyến: khách tự đưa ngựa tới hub, nên được lên trục mà không cần lượt đón
            var shared = new Scenario(scenario.type(), scenario.route(), scenario.origin(), scenario.destination(),
                    scenario.pickup(), scenario.delivery(), 1, false, true, false, scenario.leadDays());
            confirmBooking(shared, departure, day.minusDays(1));
        }
        NOW.set(departureAt.minus(Duration.ofHours(36)));
        ok(act(manager, "/departures/" + departure + "/confirm-run", "{}"));
        operate(scenario, departure, departureAt, booking);
    }

    // Một chuyến 06:00 giờ Việt Nam, chốt nhận đơn trước 72 giờ, một xe nền
    private long departureWithBaseVehicle(long route, LocalDate day, Instant departureAt, long vehicleClass) {
        var planned = departureAt.atZone(QuotationPolicy.BUSINESS_ZONE).toOffsetDateTime();
        long departure = jdbc.queryForObject("""
                INSERT INTO departure (route_id, departure_date, planned_departure_at, booking_cutoff_at, stall_capacity, status)
                SELECT ?, ?, ?, ?, max_stall_count, 'OPEN' FROM vehicle_class WHERE id = ? RETURNING id
                """, Long.class, route, day, planned, planned.minusHours(72), vehicleClass);
        jdbc.update("""
                INSERT INTO departure_vehicle (departure_id, vehicle_class_id, vehicle_count, allotment_type, created_at)
                VALUES (?, ?, 1, 'BASE', now())
                """, departure, vehicleClass);
        return departure;
    }

    private long confirmBooking(Scenario scenario, long departure, LocalDate pickup) throws Exception {
        long booking = submit(scenario, departure, pickup);
        String path = "/bookings/" + booking;
        assertThat(bookingStatus(booking)).isEqualTo("PENDING_INTAKE");
        int seats = scenario.privateTruck() ? 0 : scenario.horses() + (scenario.wideWithVet() ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT booked_stall_count FROM departure WHERE id = ?",
                Integer.class, departure)).isEqualTo(seats);
        List<Long> uploadedDocuments = uploadCustomerDocuments(booking);
        act(manager, path + "/intake", "{\"specialistId\":%d}".formatted(specialistStaff.getUserId()))
                .andExpect(status().isOk());
        assertThat(bookingStatus(booking)).isEqualTo("UNDER_REVIEW");
        approveDocuments(booking, uploadedDocuments);
        uploadCompanyDocuments(booking, false, null);
        if (!scenario.selfPickup()) {
            uploadCompanyDocuments(booking, true, null);
        }
        confirmInsurance(booking);
        act(specialist, path + "/start-quoting", "{}").andExpect(status().isOk());
        assertThat(bookingStatus(booking)).isEqualTo("QUOTING");
        long quotation = id(act(manager, path + "/quotations", "{}"));
        if (scenario.privateTruck()) {
            assertPrivateQuotation(booking, departure, quotation);
        } else if (scenario.wideWithVet()) {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM quotation_line q JOIN billable_service s ON s.id = q.billable_service_id
                    WHERE q.quotation_id = ? AND s.code = 'VET_CHECK' AND q.quantity = 1
                    """, Integer.class, quotation)).isEqualTo(1);
        }
        act(manager, "/quotations/" + quotation + "/send", "{}").andExpect(status().isOk());
        assertThat(bookingStatus(booking)).isEqualTo("AWAITING_DEPOSIT");
        act(customer, "/quotations/" + quotation + "/accept", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        pay(booking, "DEPOSIT");
        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
        return booking;
    }

    private void operate(Scenario scenario, long departure, Instant departureAt, long booking) throws Exception {
        String coordinator = login(staff("COORDINATOR", scenario.origin()).getUser().getUsername(), "staff");
        var operations = new BenXeJourneyOperations(mvc, jdbc, NOW, coordinator);
        var horses = jdbc.query("""
                SELECT bh.id, h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id
                WHERE bh.booking_id = ? ORDER BY bh.id
                """, (row, i) -> new Horse(row.getLong(1), row.getString(2)), booking);
        Instant pickupAt = departureAt.minus(Duration.ofHours(22));
        if (!scenario.selfPickup()) {
            runPickup(scenario, booking, operations, horses, pickupAt);
        }
        long linehaul = operations.allocateLinehaul(departure, horses, scenario.privateTruck());
        Crew linehaulCrew = crew(operations, linehaul, scenario.origin());
        if (scenario.selfPickup()) {
            Horse horse = horses.getFirst();
            long stay = operations.appoint(coordinator, horse.id(), pickupAt);
            // Giấy cửa khẩu cũng được kiểm khi tiếp nhận ngựa tại hub.
            operations.selfDropOff(coordinator, stay, horse.chip(), pickupAt).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.data.documents").isNotEmpty());
            uploadCompanyDocuments(booking, true, LocalDate.ofInstant(departureAt, clock.getZone()));
            ok(operations.selfDropOff(coordinator, stay, horse.chip(), pickupAt));
            pay(booking, "BALANCE");
        }
        operations.assertStay(booking, scenario.origin(), "ASSEMBLY", horses.size());
        List<Stop> stops = operations.stops(linehaul);
        operations.check(linehaul, stops.getFirst(), linehaulCrew, horses, "BEFORE_DEPARTURE");
        operations.start(linehaul, linehaulCrew);
        for (Stop stop : stops) {
            operations.arrive(linehaulCrew, stop);
            if (stop.equals(stops.getFirst())) {
                operations.handover(linehaul, stop, linehaulCrew, horses, "HUB_OUT", "{}");
            } else if (stop.equals(stops.getLast())) {
                operations.handover(linehaul, stop, linehaulCrew, horses, "HUB_IN", "{}");
                break;
            } else if (List.of("REST", "VITAL_CHECK").contains(stop.type())) {
                operations.rest(linehaulCrew, stop, horses);
            }
            if (scenario.selfPickup() && stop.type().equals("BORDER")) {
                operations.assertExpiredBorderDocuments(linehaulCrew, stop);
            }
            operations.depart(linehaulCrew, stop);
        }
        operations.completeAtFinalHub(linehaul, linehaulCrew);
        if (!scenario.type().equals("INTERPROVINCE")) {
            operations.assertBorder(linehaul, scenario.selfPickup() ? "GATE-MOCBAI" : "GATE-CAUTREO");
        }
        operations.assertStay(booking, scenario.destination(), "ASSEMBLY", horses.size());
        Instant deliveryAt = LocalDate.ofInstant(stops.getLast().arrival(), clock.getZone()).plusDays(1)
                .atTime(8, 0).atZone(clock.getZone()).toInstant();
        runDelivery(scenario, booking, operations, horses, deliveryAt);
        assertThat(bookingStatus(booking)).isEqualTo("DELIVERED");
        String path = "/bookings/" + booking;
        ok(act(customer, path + "/delivery-complaints/confirm", "{\"confirmed\":true}"));
        String settlement = ok(act(manager, path + "/settlement/complete", "{}"));
        assertThat(new BigDecimal(JsonPath.read(settlement, "$.data.outstanding").toString())).isEqualByComparingTo("0");
        assertThat(bookingStatus(booking)).isEqualTo("COMPLETED");
        operations.returnToHome(linehaul, linehaulCrew, deliveryAt.plus(Duration.ofHours(4)));
        operations.assertCompletion(booking, horses, scenario.selfPickup(), scenario.origin(), scenario.destination());
    }

    private void runPickup(Scenario scenario, long booking, BenXeJourneyOperations operations, List<Horse> horses,
                           Instant start) throws Exception {
        long hub = hub(scenario.origin());
        long vehicle = branchVehicle(hub, scenario.wideWithVet() || scenario.privateTruck());
        long trip = operations.planBranch("PICKUP", vehicle, hub, address(booking, "PICKUP"), start, null, horses);
        Crew crew = crew(operations, trip, scenario.origin());
        operations.assertPickupFleet(trip, hub);
        var stops = operations.stops(trip);
        operations.start(trip, crew);
        operations.arrive(crew, stops.getFirst());
        operations.depart(crew, stops.getFirst());
        operations.arrive(crew, stops.get(1));
        operations.handover(trip, stops.get(1), crew, horses, "PICKUP", "{}");
        pay(booking, "BALANCE");
        operations.depart(crew, stops.get(1));
        assertThat(bookingStatus(booking)).isEqualTo("IN_OPERATION");
        operations.arrive(crew, stops.getLast());
        operations.handover(trip, stops.getLast(), crew, horses, "HUB_IN", "{}");
        operations.completeAtFinalHub(trip, crew);
    }

    private void runDelivery(Scenario scenario, long booking, BenXeJourneyOperations operations, List<Horse> horses,
                             Instant start) throws Exception {
        long hub = hub(scenario.destination());
        long trip = operations.planBranch("DELIVERY", branchVehicle(hub, scenario.wideWithVet() || scenario.privateTruck()), hub,
                address(booking, "DELIVERY"), start, scenario.wideWithVet() ? hub("REST-HUE") : null, horses);
        Crew crew = crew(operations, trip, scenario.destination());
        var stops = operations.stops(trip);
        operations.check(trip, stops.getFirst(), crew, horses, "BEFORE_DELIVERY");
        operations.start(trip, crew);
        operations.arrive(crew, stops.getFirst());
        operations.handover(trip, stops.getFirst(), crew, horses, "HUB_OUT", "{}");
        operations.depart(crew, stops.getFirst());
        if (scenario.wideWithVet()) {
            Stop rest = stops.get(1);
            operations.arrive(crew, rest);
            operations.rest(crew, rest, horses);
            operations.depart(crew, rest);
        }
        Stop delivery = stops.get(stops.size() - 2);
        operations.arrive(crew, delivery);
        String code = jdbc.queryForObject("SELECT code FROM booking WHERE id = ?", String.class, booking);
        String collection = JsonPath.read(read(customer, "/bookings/" + booking + "/collection-code"), "$.data.collectionCode");
        operations.handover(trip, delivery, crew, horses, "DELIVERY", """
                {"recipients":{"%s":{"recipientName":"Người nhận M9","collectionCode":"%s"}}}
                """.formatted(code, collection));
        operations.depart(crew, delivery);
        operations.arrive(crew, stops.getLast());
        operations.completeAtFinalHub(trip, crew);
    }

    private Crew crew(BenXeJourneyOperations operations, long trip, String hub) throws Exception {
        Staff driver = staff("DRIVER", hub);
        Staff escort = staff("ESCORT", hub);
        operations.assignCrew(trip, driver, escort);
        return new Crew(login(driver.getUser().getUsername(), "staff"), login(escort.getUser().getUsername(), "staff"),
                driver.getUserId(), escort.getUserId());
    }

    private long hub(String code) {
        return jdbc.queryForObject("SELECT id FROM location WHERE code = ?", Long.class, code);
    }

    private long branchVehicle(long hub, boolean wide) {
        return jdbc.queryForObject("""
                SELECT id FROM vehicle WHERE home_hub_location_id = ? AND status = 'ACTIVE' AND service_role = 'BRANCH'
                  AND standard_stall_count >= 2 AND wide_stall_count >= ? ORDER BY id LIMIT 1
                """, Long.class, hub, wide ? 1 : 0);
    }

    private long address(long booking, String role) {
        return jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = ?",
                Long.class, booking, role);
    }

    private void assertPrivateQuotation(long booking, long departure, long quotation) {
        int capacity = jdbc.queryForObject("""
                SELECT c.max_stall_count FROM departure_vehicle a JOIN vehicle_class c ON c.id = a.vehicle_class_id
                WHERE a.booking_id = ? AND a.departure_id = ?
                    AND a.allotment_type = 'PRIVATE' AND a.vehicle_count = 1
                """, Integer.class, booking, departure);
        assertThat(capacity).isGreaterThan(3);
        assertThat(jdbc.queryForObject("""
                SELECT sum(q.quantity * CASE p.stall_type_code WHEN 'WIDE' THEN 2 ELSE 1 END)
                FROM quotation_line q JOIN price_item p ON p.id = q.price_item_id
                WHERE q.quotation_id = ? AND q.line_type = 'LINEHAUL'
                """, BigDecimal.class, quotation)).isEqualByComparingTo(BigDecimal.valueOf(capacity));
    }

    private long submit(Scenario scenario, long departure, LocalDate pickup) throws Exception {
        String party = "{\"bookingRole\":\"%s\",\"fullName\":\"Người thử\",\"phone\":\"0901234567\",\"address\":\"Địa chỉ thử\"}";
        String parties = String.join(",", List.of("SHIPPER", "CONSIGNEE", "PAYER", "EMERGENCY_CONTACT")
                .stream().map(party::formatted).toList());
        var horses = new ArrayList<String>();
        for (int index = 0; index < scenario.horses(); index++) {
            long horse = jdbc.queryForObject("""
                    INSERT INTO horse (owner_customer_id, name, microchip, sex, horse_type_id, status)
                    SELECT u.id, ?, ?, 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE'
                    FROM app_user u WHERE u.username = ? RETURNING id
                    """, Long.class, data.unique("M9 horse"), "%015d".formatted(System.nanoTime() % 1_000_000_000_000_000L), username);
            boolean wide = index == 0 && (scenario.wideWithVet() || scenario.privateTruck());
            String addOns = index == 0 && scenario.wideWithVet() ? """
                    [{"serviceId":%d,"quantity":1}]
                    """.formatted(jdbc.queryForObject("SELECT id FROM billable_service WHERE code = 'VET_CHECK'", Long.class)) : "[]";
            horses.add("""
                    {"horseId":%d,"stallTypeCode":"%s","addOns":%s,
                     "insurance":{"coverageOption":"NONE","acceptedWaiver":true}}
                    """.formatted(horse, wide ? "WIDE" : "STANDARD", addOns));
        }
        return id(act(customer, "/bookings", """
                {"bookingType":"%s","departureId":%d,"requestedPickupDate":"%s","acceptedServiceTerms":true,
                 "pickupMethod":"%s","deliveryMethod":"COMPANY","transportMode":"%s",
                 "parties":[%s],"addresses":[%s,%s],"horses":[%s]}
                """.formatted(scenario.type(), departure, pickup, scenario.selfPickup() ? "SELF" : "COMPANY",
                scenario.privateTruck() ? "PRIVATE" : "SHARED", parties, scenario.pickup().address("PICKUP"),
                scenario.delivery().address("DELIVERY"), String.join(",", horses))));
    }

    private List<Long> uploadCustomerDocuments(long booking) throws Exception {
        var uploadedDocuments = new ArrayList<Long>();
        String checklist = read(customer, "/bookings/" + booking + "/documents/my-checklist");
        List<Map<String, Object>> items = JsonPath.read(checklist,
                "$.data.items[?(@.submitterType == 'CUSTOMER' && @.requirement.mandatory == true)]");
        assertThat(items).isNotEmpty();
        for (var item : items) {
            var upload = multipart("/bookings/{id}/documents/upload", booking)
                    .file(new MockMultipartFile("file", "giay.pdf", "application/pdf", "%PDF-1.4".getBytes()));
            upload.param("requirementId", ((Map<?, ?>) item.get("requirement")).get("id").toString());
            upload.param("expiryDate", LocalDate.now(QuotationPolicy.BUSINESS_ZONE).plusYears(1).toString());
            if (item.get("bookingHorseId") != null) {
                upload.param("bookingHorseId", item.get("bookingHorseId").toString());
            }
            upload.header(HttpHeaders.AUTHORIZATION, customer);
            uploadedDocuments.add(id(mvc.perform(upload)));
        }
        return uploadedDocuments;
    }

    private void uploadCompanyDocuments(long booking, boolean borderOnly, LocalDate expiry) throws Exception {
        String path = "/bookings/" + booking + "/documents";
        String checklist = read(specialist, path + "/checklist");
        List<Map<String, Object>> items = JsonPath.read(checklist,
                "$.data.items[?(@.submitterType != 'CUSTOMER' && @.requirement.mandatory == true)]");
        for (var item : items) {
            var requirement = (Map<?, ?>) item.get("requirement");
            boolean border = requirement.get("gateLocationId") != null;
            if (border != borderOnly) {
                continue;
            }
            var request = multipart(path + "/upload-for-partner")
                    .file(new MockMultipartFile("file", "border.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                    .param("requirementId", requirement.get("id").toString())
                    .param("expiryDate", (expiry == null ? LocalDate.now(clock).plusYears(1) : expiry).toString());
            if (item.get("bookingHorseId") != null) {
                request.param("bookingHorseId", item.get("bookingHorseId").toString());
            }
            // Chuyên viên nộp là đã duyệt
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, specialist)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));
        }
    }

    private void approveDocuments(long booking, List<Long> uploadedDocuments) throws Exception {
        for (Long document : uploadedDocuments) {
            act(specialist, "/bookings/" + booking + "/documents/" + document + "/approve", "{}")
                    .andExpect(status().isOk());
        }
    }

    private void confirmInsurance(long booking) throws Exception {
        for (Long insurance : jdbc.queryForList("""
                SELECT i.id FROM horse_insurance i JOIN booking_horse h ON h.id = i.booking_horse_id WHERE h.booking_id = ?
                """, Long.class, booking)) {
            act(specialist, "/bookings/" + booking + "/insurances/" + insurance + "/confirm", "{}").andExpect(status().isOk());
        }
    }

    private void pay(long booking, String type) throws Exception {
        String payments = read(customer, "/bookings/" + booking + "/payments");
        long payment = ((List<Number>) JsonPath.read(payments, "$.data[?(@.type == '" + type + "')].id")).getFirst().longValue();
        BigDecimal amount = new BigDecimal(((List<Number>) JsonPath.read(payments, "$.data[?(@.type == '" + type + "')].amount")).getFirst().toString());
        String url = JsonPath.read(act(customer, "/payments/" + payment + "/checkout", "{}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.paymentUrl");
        var params = new HashMap<String, String>();
        params.put("vnp_TxnRef", UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("vnp_TxnRef"));
        params.put("vnp_TmnCode", "FLOWTEST");
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigIntegerExact().toString());
        params.put("vnp_PayDate", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(QuotationPolicy.BUSINESS_ZONE).format(clock.instant()));
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", String.valueOf(System.nanoTime() % 1_000_000_000));
        params.put("vnp_SecureHash", VnpayUtil.sign(params, "flow-test-signature"));
        var ipn = get("/payments/vnpay/ipn");
        params.forEach(ipn::param);
        mvc.perform(ipn).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
    }

    private ResultActions act(String token, String path, String body) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String read(String token, String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String bookingStatus(long booking) {
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking);
    }

    private static long id(ResultActions result) throws Exception {
        return BenXeJourneyOperations.number(ok(result), "$.data.id");
    }

    private Staff staff(String role, String hub) {
        return data.staff(data.unique("m9." + role.toLowerCase(java.util.Locale.ROOT)), role, hub);
    }

    private String login(String user, String realm) throws Exception {
        String response = ok(mvc.perform(post("/auth/" + realm + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, TestData.PASSWORD))));
        return "Bearer " + JsonPath.read(response, "$.data.accessToken");
    }

    private String actWithoutAuthentication(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}