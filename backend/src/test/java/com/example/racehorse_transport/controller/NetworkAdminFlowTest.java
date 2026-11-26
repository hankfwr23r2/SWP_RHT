package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.repository.HubServiceAreaRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Quản trị danh mục mạng lưới qua API: địa điểm, vùng phục vụ, tuyến kèm điểm dừng, lịch chạy, và phân quyền.
@PostgresIntegrationTest
@AutoConfigureMockMvc
class NetworkAdminFlowTest {
    @Autowired private com.example.racehorse_transport.repository.VehicleClassRepository vehicleClasses;

    @Autowired private MockMvc mvc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private HubServiceAreaRepository serviceAreas;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void tokens() {
        String admin = testData.unique("admin");
        testData.staff(admin, "ADMIN");
        adminToken = jwtUtils.generateToken(admin, List.of("ADMIN"));
        String customer = testData.unique("khach");
        testData.customer(customer);
        customerToken = jwtUtils.generateToken(customer, List.of("CUSTOMER"));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    private long createLocation(String code, String type, String provinceCode) throws Exception {
        String body = """
                {"code":"%s","name":"Địa điểm %s","locationType":"%s","countryCode":"VN","provinceCode":"%s",
                 "address":"Địa chỉ thử","latitude":10.9,"longitude":106.6}""".formatted(code, code, type, provinceCode);
        String json = call(post("/locations"), adminToken, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private String uniqueCode(String prefix) {
        return testData.unique(prefix).toUpperCase(Locale.ROOT);
    }

    @Test
    void provincesArePublic() throws Exception {
        call(get("/provinces").param("country", "VN"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(34));
        call(get("/countries"), null, null).andExpect(status().isOk());
    }

    @Test
    void locationExposesLocalTimesAsStrings() throws Exception {
        String body = """
                {"code":"%s","name":"Cửa khẩu","locationType":"BORDER_GATE","countryCode":"VN",
                 "counterpartCountryCode":"KH","address":"Địa chỉ","openingTime":"00:00:00","closingTime":"23:59:00"}
                """.formatted(uniqueCode("GATE"));
        call(post("/locations"), adminToken, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.locationType").value("BORDER_GATE"))
                .andExpect(jsonPath("$.data.counterpartCountryCode").value("KH"))
                .andExpect(jsonPath("$.data.openingTime").value("00:00:00"))
                .andExpect(jsonPath("$.data.closingTime").value("23:59:00"));
    }

    @Test
    void borderGateNeedsForeignCounterpartAndQuarantineFarmIsNotAccepted() throws Exception {
        String gate = """
                {"code":"%s","name":"Cửa khẩu","locationType":"BORDER_GATE","countryCode":"VN"%s,"address":"Địa chỉ"}""";
        call(post("/locations"), adminToken, gate.formatted(uniqueCode("GATE"), "")).andExpect(status().isBadRequest());
        call(post("/locations"), adminToken, gate.formatted(uniqueCode("GATE"), ",\"counterpartCountryCode\":\"VN\""))
                .andExpect(status().isBadRequest());

        String code = uniqueCode("REST");
        long id = createLocation(code, "REST_STOP", "VN-1");
        String farm = """
                {"code":"%s","name":"Trại","locationType":"QUARANTINE_FARM","countryCode":"VN","address":"Địa chỉ"}""";
        call(post("/locations"), adminToken, farm.formatted(uniqueCode("QF"))).andExpect(status().isBadRequest());
        call(put("/locations/" + id), adminToken, farm.formatted(code)).andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminManagesLocationsAndCodesAreUnique() throws Exception {
        String code = uniqueCode("HUB");
        String body = """
                {"code":"%s","name":"Hub","locationType":"HUB","countryCode":"VN","address":"Địa chỉ"}""".formatted(code);
        call(post("/locations"), customerToken, body).andExpect(status().isForbidden());
        call(post("/locations"), null, body).andExpect(status().isUnauthorized());
        createLocation(code, "HUB", "VN-1");
        call(post("/locations"), adminToken, body).andExpect(status().isConflict());

        // Tỉnh phải thuộc đúng quốc gia đã chọn
        call(post("/locations"), adminToken, """
                {"code":"%s","name":"Sai tỉnh","locationType":"HUB","countryCode":"VN","provinceCode":"KH-12","address":"Địa chỉ"}"""
                .formatted(uniqueCode("HUB"))).andExpect(status().isBadRequest());
        // Khách đã đăng nhập vẫn xem được danh sách địa điểm
        call(get("/locations").param("type", "HUB"), customerToken, null).andExpect(status().isOk());
    }

    @Test
    void provinceMovesBetweenHubsAndOnlyHubsServeProvinces() throws Exception {
        long newHub = createLocation(uniqueCode("HUB"), "HUB", "VN-1");
        long restStop = createLocation(uniqueCode("REST"), "REST_STOP", "VN-1");
        long hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();

        call(put("/hub-service-areas/VN-1"), adminToken, "{\"hubLocationId\":%d}".formatted(newHub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hubLocationId").value(newHub));
        call(put("/hub-service-areas/VN-1"), adminToken, "{\"hubLocationId\":%d}".formatted(hcm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hubCode").value("HUB-HCM"));
        assertThat(serviceAreas.findById_ProvinceCode("VN-1").orElseThrow().getHub().getId()).isEqualTo(hcm);

        call(put("/hub-service-areas/VN-1"), adminToken, "{\"hubLocationId\":%d}".formatted(restStop))
                .andExpect(status().isBadRequest());
        call(delete("/hub-service-areas/VN-1"), adminToken, null).andExpect(status().isOk());
        assertThat(serviceAreas.findById_ProvinceCode("VN-1")).isEmpty();
    }

    @Test
    void routeWithStopsCanBeCreatedReplacedAndScheduled() throws Exception {
        long origin = createLocation(uniqueCode("HUB"), "HUB", "VN-79");
        long rest = createLocation(uniqueCode("REST"), "REST_STOP", "VN-80");
        long gate = locations.findByCode("GATE-MOCBAI").orElseThrow().getId();
        long destination = locations.findByCode("HUB-PNH").orElseThrow().getId();
        String code = uniqueCode("R");

        // Điểm dừng đầu không phải điểm đầu tuyến: bị từ chối
        call(post("/routes"), adminToken, routeBody(code, origin, destination, """
                [{"sequenceNumber":1,"locationId":%d,"stopType":"REST","arrivalOffsetMinutes":0,"dwellMinutes":10},
                 {"sequenceNumber":2,"locationId":%d,"stopType":"HUB","arrivalOffsetMinutes":180,"dwellMinutes":0}]""".formatted(rest, destination)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("điểm đầu tuyến")));

        String created = call(post("/routes"), adminToken, routeBody(code, origin, destination, """
                [{"sequenceNumber":1,"locationId":%d,"stopType":"HUB","arrivalOffsetMinutes":0,"dwellMinutes":30},
                 {"sequenceNumber":2,"locationId":%d,"stopType":"BORDER","arrivalOffsetMinutes":90,"dwellMinutes":60},
                 {"sequenceNumber":3,"locationId":%d,"stopType":"HUB","arrivalOffsetMinutes":180,"dwellMinutes":0}]""".formatted(origin, gate, destination)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stops.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        long routeId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

        call(put("/routes/" + routeId), adminToken, routeBody(code, origin, destination, """
                [{"sequenceNumber":1,"locationId":%d,"stopType":"HUB","arrivalOffsetMinutes":0,"dwellMinutes":30},
                 {"sequenceNumber":2,"locationId":%d,"stopType":"REST","arrivalOffsetMinutes":70,"dwellMinutes":30},
                 {"sequenceNumber":3,"locationId":%d,"stopType":"BORDER","arrivalOffsetMinutes":120,"dwellMinutes":60},
                 {"sequenceNumber":4,"locationId":%d,"stopType":"HUB","arrivalOffsetMinutes":210,"dwellMinutes":0}]""".formatted(origin, rest, gate, destination)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stops.length()").value(4))
                .andExpect(jsonPath("$.data.stops[1].locationId").value(rest));

        // Địa điểm đang thuộc tuyến đang chạy thì không ngừng dùng được
        call(post("/locations/" + rest + "/deactivate"), adminToken, null).andExpect(status().isConflict());

        String schedule = call(post("/departure-schedules"), adminToken, """
                {"routeId":%d,"departureTime":"06:00","bookingCutoffHours":72,"baseVehicleClassId":%d,"baseVehicleCount":1,
                 "validFrom":"2026-12-01","daysOfWeek":[5,2]}""".formatted(routeId, vehicleClasses.findByCode("HEAVY").orElseThrow().getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.daysOfWeek[0]").value(2))
                .andReturn().getResponse().getContentAsString();
        long departureScheduleId = ((Number) JsonPath.read(schedule, "$.data.id")).longValue();

        call(post("/departure-schedules/" + departureScheduleId + "/pause"), adminToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAUSED"));
        call(post("/departure-schedules/" + departureScheduleId + "/end"), adminToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"));
        call(post("/departure-schedules/" + departureScheduleId + "/resume"), adminToken, null).andExpect(status().isConflict());
        call(get("/departure-schedules").param("routeId", String.valueOf(routeId)), customerToken, null).andExpect(status().isForbidden());
    }

    @Test
    void onlyOneDirectionOfARoutePairCanBeOutbound() throws Exception {
        long origin = createLocation(uniqueCode("HUB"), "HUB", "VN-79");
        long destination = createLocation(uniqueCode("HUB"), "HUB", "VN-80");
        String outCode = uniqueCode("R");
        String backCode = uniqueCode("R");
        String created = call(post("/routes"), adminToken, fleetRouteBody(outCode, origin, destination, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fleetOutbound").value(true))
                .andReturn().getResponse().getContentAsString();
        long outbound = ((Number) JsonPath.read(created, "$.data.id")).longValue();

        String claim = fleetRouteBody(backCode, destination, origin, true);
        call(post("/routes"), adminToken, claim).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cặp tuyến chỉ có một chiều đi."));
        String reverse = call(post("/routes"), adminToken, fleetRouteBody(backCode, destination, origin, false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fleetOutbound").value(false))
                .andReturn().getResponse().getContentAsString();
        long inbound = ((Number) JsonPath.read(reverse, "$.data.id")).longValue();

        call(put("/routes/" + inbound), adminToken, claim).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cặp tuyến chỉ có một chiều đi."));
        call(put("/routes/" + outbound), adminToken, fleetRouteBody(outCode, origin, destination, false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fleetOutbound").value(false));
        call(put("/routes/" + inbound), adminToken, claim)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fleetOutbound").value(true));
    }

    private static String fleetRouteBody(String code, long origin, long destination, boolean outbound) {
        return """
                {"code":"%s","name":"Tuyến thử","originLocationId":%d,"destinationLocationId":%d,
                 "distanceKm":75.5,"estimatedDurationMinutes":120,"fleetOutbound":%b,
                 "stops":[{"sequenceNumber":1,"locationId":%d,"stopType":"HUB","arrivalOffsetMinutes":0,"dwellMinutes":30},
                          {"sequenceNumber":2,"locationId":%d,"stopType":"HUB","arrivalOffsetMinutes":120,"dwellMinutes":0}]}"""
                .formatted(code, origin, destination, outbound, origin, destination);
    }

    private static String routeBody(String code, long origin, long destination, String stops) {
        return """
                {"code":"%s","name":"Tuyến thử","originLocationId":%d,"destinationLocationId":%d,
                 "distanceKm":75.5,"estimatedDurationMinutes":180,"stops":%s}""".formatted(code, origin, destination, stops);
    }
}
