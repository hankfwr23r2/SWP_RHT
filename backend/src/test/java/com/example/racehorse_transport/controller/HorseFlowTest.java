package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
class HorseFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;

    @Test
    void customerManagesHorsesAndOthersGetNotFound() throws Exception {
        String token = customerToken();
        long type = horseType(token);
        String body = body(chip(), type);
        String response = mvc.perform(post("/horses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.data.id")).longValue();
        String chip = JsonPath.read(response, "$.data.microchip");

        // Gửi kèm chip và trạng thái khác: bị bỏ qua
        mvc.perform(put("/horses/{id}", id).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content(body(chip(), type).replace("Sao Mai", "Sao Hom")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Sao Hom"))
                .andExpect(jsonPath("$.data.microchip").value(chip)).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(get("/horses").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/horses/{id}", id).header(HttpHeaders.AUTHORIZATION, customerToken())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidChipStaffAndAnonymous() throws Exception {
        String token = customerToken();
        mvc.perform(post("/horses").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(body("123", horseType(token)))).andExpect(status().isBadRequest());
        String staff = data.unique("horse-staff");
        data.staff(staff, "MANAGER");
        mvc.perform(get("/horses").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(staff, List.of("MANAGER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/horses")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateChipReturnsConflict() throws Exception {
        String token = customerToken();
        String body = body(chip(), horseType(token));
        mvc.perform(post("/horses").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/horses").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    private String body(String chip, long type) {
        return """
                {"microchip":"%s","name":"Sao Mai","sex":"MARE","birthYear":2019,"horseTypeId":%d,"status":"DECEASED"}
                """.formatted(chip, type);
    }

    private long horseType(String token) throws Exception {
        String response = mvc.perform(get("/horse-types").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data[0].id")).longValue();
    }

    private String customerToken() {
        String username = data.unique("horse-a5");
        data.customer(username);
        return "Bearer " + jwt.generateToken(username, List.of("CUSTOMER"));
    }

    private String chip() {
        return "%015d".formatted(900_000_000_000_000L + System.nanoTime() % 1_000_000_000_000L);
    }
}
