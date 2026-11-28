package com.example.racehorse_transport.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Gợi ý chuyến cho khách theo 4 loại đơn. Dùng chuyến do seed dev sinh từ lịch.
@PostgresIntegrationTest
@AutoConfigureMockMvc
class DepartureFlowTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

    @Autowired private MockMvc mvc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String customerToken;
    private String managerToken;

    @BeforeEach
    void tokens() {
        String customer = testData.unique("khach");
        testData.customer(customer);
        customerToken = jwtUtils.generateToken(customer, List.of("CUSTOMER"), 0);
        String manager = testData.unique("quanly");
        testData.staff(manager, "MANAGER");
        managerToken = jwtUtils.generateToken(manager, List.of("MANAGER"), 0);
    }

    private MockHttpServletRequestBuilder suggestions(String type, String pickupProvince, String deliveryProvince) {
        return get("/departures/suggestions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                .param("bookingType", type)
                .param("pickupProvinceCode", pickupProvince)
                .param("deliveryProvinceCode", deliveryProvince)
                .param("fromDate", TODAY);
    }

    @Test
    void sameHubRunsDirectWithoutDeparture() throws Exception {
        // Đồng Nai -> TP.HCM: cùng HUB-HCM
        mvc.perform(suggestions("INTERPROVINCE", "VN-75", "VN-79"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routing").value("DIRECT"))
                .andExpect(jsonPath("$.data.pickupHubCode").value("HUB-HCM"))
                .andExpect(jsonPath("$.data.departures.length()").value(0));
    }

    @Test
    void domesticTrunkSuggestsLinehaulDepartures() throws Exception {
        // Đồng Nai -> Huế: HUB-HCM -> HUB-DN
        mvc.perform(suggestions("INTERPROVINCE", "VN-75", "VN-46"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routing").value("LINEHAUL"))
                .andExpect(jsonPath("$.data.deliveryHubCode").value("HUB-DN"))
                .andExpect(jsonPath("$.data.departures.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data.departures[*].routeCode", everyItem(is("HCM-DN"))));
    }

    @Test
    void privateSuggestionsKeepSoldOutDeparturesWithFullLabel() throws Exception {
        mvc.perform(suggestions("INTERPROVINCE", "VN-75", "VN-46")
                        .param("transportMode", "PRIVATE").param("stalls", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departures.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data.departures[*].availability", everyItem(is("FULL"))));
        mvc.perform(suggestions("INTERPROVINCE", "VN-75", "VN-46")
                        .param("transportMode", "PRIVATE").param("stalls", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departures[*].availability",
                        org.hamcrest.Matchers.hasItem("AVAILABLE")));
    }

    @Test
    void exportAndImportUseBothHubs() throws Exception {
        mvc.perform(suggestions("EXPORT", "VN-79", "KH-12").param("deliveryLatitude", "11.5564").param("deliveryLongitude", "104.9282"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryHubCode").value("HUB-PNH"))
                .andExpect(jsonPath("$.data.departures.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data.departures[*].routeCode", everyItem(is("HCM-PNH"))));

        mvc.perform(suggestions("IMPORT", "KH-12", "VN-79").param("pickupLatitude", "11.5564").param("pickupLongitude", "104.9282"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routing").value("LINEHAUL"))
                .andExpect(jsonPath("$.data.departures.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data.pickupHubCode").value("HUB-PNH"))
                .andExpect(jsonPath("$.data.departures[*].routeCode", everyItem(is("PNH-HCM"))));
    }

    @Test
    void saysWhichSideHasNoHubAndWhereTheCompanyServes() throws Exception {
        // Dữ liệu mẫu phục vụ đủ 34 tỉnh: tạm bỏ Điện Biên khỏi vùng hub để có một tỉnh chưa phục vụ
        Long hubId = jdbc.queryForObject("SELECT hub_location_id FROM hub_service_area WHERE province_code = 'VN-11'", Long.class);
        jdbc.update("DELETE FROM hub_service_area WHERE province_code = 'VN-11'");
        try {
            unservedSides();
        } finally {
            jdbc.update("INSERT INTO hub_service_area (hub_location_id, province_code) VALUES (?, 'VN-11')", hubId);
        }
    }

    private void unservedSides() throws Exception {
        mvc.perform(suggestions("INTERPROVINCE", "VN-11", "VN-48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailable").value("PICKUP_NOT_SERVED"))
                .andExpect(jsonPath("$.data.servedProvinces.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data.departures.length()").value(0));
        mvc.perform(suggestions("IMPORT", "KH-12", "VN-11"))
                .andExpect(jsonPath("$.data.unavailable").value("DELIVERY_NOT_SERVED"));
        mvc.perform(suggestions("EXPORT", "VN-79", "KH-1"))
                .andExpect(jsonPath("$.data.unavailable").value("DELIVERY_NOT_SERVED"));
        mvc.perform(suggestions("IMPORT", "KH-1", "VN-79"))
                .andExpect(jsonPath("$.data.unavailable").value("PICKUP_NOT_SERVED"));
        // Hub Đà Nẵng chỉ có tuyến quốc tế đi Lào
        mvc.perform(suggestions("EXPORT", "VN-48", "KH-12"))
                .andExpect(jsonPath("$.data.unavailable").value("NO_ROUTE"))
                .andExpect(jsonPath("$.data.pickupHubName").isNotEmpty());
    }

    @Test
    void marksDeparturesTooCloseToFinishThePapers() throws Exception {
        int id = Math.toIntExact(testData.departure("HCM-DN", LocalDate.now().plusDays(4), 6).getId());
        mvc.perform(suggestions("INTERPROVINCE", "VN-75", "VN-46"))
                .andExpect(jsonPath("$.data.leadDays", greaterThan(0)))
                .andExpect(jsonPath("$.data.tooLateDepartureIds", org.hamcrest.Matchers.hasItem(id)));
    }

    @Test
    void bookingTypeMustMatchPlaces() throws Exception {
        mvc.perform(suggestions("LOCAL", "VN-75", "VN-79")).andExpect(status().isBadRequest());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void adHocCreationAndDepartureMergeAreNotOffered() throws Exception {
        long source = testData.departure("HCM-DN", LocalDate.now().plusDays(200), 9).getId();
        long target = testData.departure("HCM-DN", LocalDate.now().plusDays(201), 9).getId();
        var owner = testData.customer(testData.unique("khach"));
        long booking = testData.booking(owner, com.example.racehorse_transport.entity.enums.BookingType.INTERPROVINCE,
                com.example.racehorse_transport.entity.enums.BookingStatus.SCHEDULE_CHANGE_REQUIRED).getId();
        String ownerToken = jwtUtils.generateToken(owner.getUser().getUsername(), List.of("CUSTOMER"), 0);

        mvc.perform(post("/departures").header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/departures/{id}/merge-targets", source).header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/departures/{id}/merge", source).header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDepartureId\":%d,\"reason\":\"Ít khách\"}".formatted(target)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/bookings/{id}/reschedule-suggestion", booking).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }
}
