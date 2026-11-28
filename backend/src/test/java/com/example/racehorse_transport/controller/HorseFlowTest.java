package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.example.racehorse_transport.policy.BookingSubmissionPolicy.businessDate;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
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
    @Autowired private Clock clock;

    @Test
    void customerManagesHorsesAndOthersGetNotFound() throws Exception {
        String token = customerToken();
        long type = horseType(token);
        String body = body(chip(), type).replace("\"status\":\"DECEASED\"", """
                "status":"DECEASED","passportNumber":"OLD-PASS","breed":"Arabian","sire":"Sire","dam":"Dam",
                "weightKg":-1,"declaredValue":-1,"declaredCurrencyCode":"?"
                """);
        String response = mvc.perform(post("/horses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.data.id")).longValue();
        String chip = JsonPath.read(response, "$.data.microchip");

        // Jackson bỏ qua chip và trạng thái khi sửa, cùng các trường đã bỏ khỏi hồ sơ.
        mvc.perform(put("/horses/{id}", id).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace(chip, chip()).replace("Sao Mai", "Sao Hom").replace("Nâu", "Đen")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Sao Hom"))
                .andExpect(jsonPath("$.data.microchip").value(chip)).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.color").value("Đen"));
        var saved = mvc.perform(get("/horses/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        for (String field : List.of("passportNumber", "breed", "sire", "dam", "weightKg", "declaredValue", "declaredCurrencyCode")) {
            saved.andExpect(jsonPath("$.data." + field).doesNotHaveJsonPath());
        }
        mvc.perform(get("/horses").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/horses/{id}", id).header(HttpHeaders.AUTHORIZATION, customerToken())).andExpect(status().isNotFound());
    }

    @Test
    void bothCreateAndUpdateRequireColorAndABirthYearFrom1990() throws Exception {
        String token = customerToken();
        String valid = body(chip(), horseType(token));
        var creation = mvc.perform(post("/horses").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isCreated());
        int id = JsonPath.read(creation.andReturn().getResponse().getContentAsString(), "$.data.id");
        record InvalidBody(String body, String path, String error) {}
        int future = businessDate(clock.instant()).getYear() + 1;
        for (var invalid : List.of(
                new InvalidBody(valid.replace(",\"color\":\"Nâu\"", ""), "$.data.color", "Nhập màu lông."),
                new InvalidBody(valid.replace("\"Nâu\"", "\"   \""), "$.data.color", "Nhập màu lông."),
                new InvalidBody(valid.replace("\"birthYear\":2019,", ""), "$.data.birthYear", "Nhập năm sinh hợp lệ."),
                new InvalidBody(valid.replace("\"birthYear\":2019", "\"birthYear\":1989"), "$.data.birthYear", "Nhập năm sinh hợp lệ."),
                new InvalidBody(valid.replace("\"birthYear\":2019", "\"birthYear\":" + future), "$.message", "Nhập năm sinh hợp lệ."))) {
            mvc.perform(post("/horses").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(invalid.body()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath(invalid.path()).value(invalid.error()));
            mvc.perform(put("/horses/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(invalid.body()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath(invalid.path()).value(invalid.error()));
        }
    }

    @Test
    void rejectsInvalidChipStaffAndAnonymous() throws Exception {
        String token = customerToken();
        mvc.perform(post("/horses").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(body("123", horseType(token)))).andExpect(status().isBadRequest());
        String staff = data.unique("horse-staff");
        data.staff(staff, "MANAGER");
        mvc.perform(get("/horses").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(staff, List.of("MANAGER"), 0)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/horse-types").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(staff, List.of("MANAGER"), 0)))
                .andExpect(status().isForbidden());
        String admin = data.unique("horse-admin");
        data.staff(admin, "ADMIN");
        mvc.perform(get("/horse-types").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generateToken(admin, List.of("ADMIN"), 0)))
                .andExpect(status().isOk());
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
                {"microchip":"%s","name":"Sao Mai","sex":"MARE","birthYear":2019,"color":"Nâu","horseTypeId":%d,"status":"DECEASED"}
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
        return "Bearer " + jwt.generateToken(username, List.of("CUSTOMER"), 0);
    }

    private String chip() {
        return "%015d".formatted(900_000_000_000_000L + System.nanoTime() % 1_000_000_000_000L);
    }
}
