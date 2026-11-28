package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.VehicleClassRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class VehicleFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private VehicleClassRepository classes;
    @Autowired private LocationRepository locations;
    @Autowired private VehicleRepository vehicles;

    private String admin;
    private long headquarters;
    private long vehicleClass;

    @BeforeEach
    void setUp() {
        String username = testData.unique("vehicle.admin");
        testData.staff(username, "ADMIN");
        admin = "Bearer " + jwtUtils.generateToken(username, List.of("ADMIN"));
        headquarters = locations.findByCode("HUB-HCM").orElseThrow().getId();
        vehicleClass = classes.findByCode("LIGHT").orElseThrow().getId();
    }

    @Test
    void adminCreatesReadsAndUpdatesVehicles() throws Exception {
        mvc.perform(post("/vehicles").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(" a2-http-01 ", headquarters, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.licensePlate").value("A2-HTTP-01"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        long id = vehicles.findByLicensePlate("A2-HTTP-01").orElseThrow().getId();
        mvc.perform(put("/vehicles/{id}", id).header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body("A2-HTTP-01", headquarters, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.standardStallCount").value(2));
        mvc.perform(get("/vehicles/{id}", id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.homeHubLocationId").value(headquarters));
        mvc.perform(get("/vehicles").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
        mvc.perform(get("/vehicle-classes").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.code == 'LIGHT')]").isNotEmpty());
    }

    @Test
    void customerCannotReadVehicleAdministration() throws Exception {
        String username = testData.unique("vehicle.customer");
        testData.customer(username);
        mvc.perform(get("/vehicles").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtUtils.generateToken(username, List.of("CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void refusesExcessCapacity() throws Exception {
        mvc.perform(post("/vehicles").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body("A2-HTTP-02", headquarters, Integer.MAX_VALUE)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.standardStallCount").isString());
    }

    @Test
    void rejectsDuplicateNormalizedPlate() throws Exception {
        mvc.perform(post("/vehicles").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body("A2-HTTP-03", headquarters, 1)))
                .andExpect(status().isCreated());
        mvc.perform(post("/vehicles").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body(" a2-http-03 ", headquarters, 1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.licensePlate").isString());
    }

    @Test
    void cannotChangeHeadquartersToAnotherLocationType() throws Exception {
        mvc.perform(put("/locations/{id}", headquarters).header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"code":"HUB-HCM","name":"Trụ sở","locationType":"REST_STOP",
                                 "countryCode":"VN","address":"TP.HCM"}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.locationType").isString());
    }

    private String body(String plate, long hub, int stalls) {
        return """
                {"licensePlate":"%s","vehicleClassId":%d,"standardStallCount":%d,"wideStallCount":0,
                 "homeHubLocationId":%d,"id":9999,"status":"RETIRED"}
                """.formatted(plate, vehicleClass, stalls, hub);
    }
}
