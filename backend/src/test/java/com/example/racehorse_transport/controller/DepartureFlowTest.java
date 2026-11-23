package com.example.racehorse_transport.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.repository.RouteRepository;
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

// Gợi ý chuyến cho khách theo 4 loại đơn, và quản lý tạo chuyến đột xuất. Dùng chuyến do seed dev sinh từ lịch.
@PostgresIntegrationTest
@AutoConfigureMockMvc
class DepartureFlowTest {

    private static final String TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

    @Autowired private MockMvc mvc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private RouteRepository routes;

    private String customerToken;
    private String managerToken;

    @BeforeEach
    void tokens() {
        String customer = testData.unique("khach");
        testData.customer(customer);
        customerToken = jwtUtils.generateToken(customer, List.of("CUSTOMER"));
        String manager = testData.unique("quanly");
        testData.staff(manager, "MANAGER");
        managerToken = jwtUtils.generateToken(manager, List.of("MANAGER"));
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
    void exportGoesToBorderGateAndImportComesFromIt() throws Exception {
        mvc.perform(suggestions("EXPORT", "VN-79", "KH-12").param("deliveryLatitude", "11.5564").param("deliveryLongitude", "104.9282"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departures.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data.departures[*].routeCode", everyItem(is("HCM-PNH"))));

        mvc.perform(suggestions("IMPORT", "KH-12", "VN-79").param("pickupLatitude", "11.5564").param("pickupLongitude", "104.9282"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routing").value("LINEHAUL"))
                .andExpect(jsonPath("$.data.departures.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data.departures[*].routeCode", everyItem(is("MOCBAI-HCM"))));
    }

    @Test
    void bookingTypeMustMatchPlaces() throws Exception {
        mvc.perform(suggestions("LOCAL", "VN-75", "VN-79")).andExpect(status().isBadRequest());
    }

    @Test
    void managerCreatesAdHocDepartureAndCustomerCannot() throws Exception {
        long routeId = routes.findByCode("HCM-DN").orElseThrow().getId();
        String body = """
                {"routeId":%d,"departureDate":"%s","departureTime":"07:00","bookingCutoffHours":48,"stallCapacity":6}"""
                .formatted(routeId, LocalDate.now().plusDays(40));

        mvc.perform(post("/departures").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/departures").header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.departureScheduleId").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.availableStalls").value(6));
        mvc.perform(get("/departures").header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .param("routeId", String.valueOf(routeId)).param("from", TODAY).param("to", LocalDate.now().plusDays(60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThan(0)));
    }
}
