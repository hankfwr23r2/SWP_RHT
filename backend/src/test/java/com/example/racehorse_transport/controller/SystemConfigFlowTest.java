package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class SystemConfigFlowTest {

    private static final String URL = "/system-configs/OTP_MAX_ATTEMPTS";

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;

    @Test
    void adminReadsAndUpdatesExistingConfigWithActorFromToken() throws Exception {
        String token = staffToken("ADMIN");
        mvc.perform(get("/system-configs").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configValue":7,"updatedByUserId":999999,"minValue":7,"maxValue":7}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configValue").value("7"))
                .andExpect(jsonPath("$.data.minValue").value(1))
                .andExpect(jsonPath("$.data.maxValue").value(10))
                .andExpect(jsonPath("$.data.updatedByUserId").isNumber());
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configValue").value("7"))
                .andExpect(jsonPath("$.data.minValue").value(1))
                .andExpect(jsonPath("$.data.maxValue").value(10));
    }

    @Test
    void adminEditsContactTextAndEveryoneReadsIt() throws Exception {
        String admin = staffToken("ADMIN");
        mvc.perform(get("/public/contact")).andExpect(status().isOk()).andExpect(jsonPath("$.data.hotline").isNotEmpty());
        mvc.perform(put("/system-configs/HOTLINE/text").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"configValue\":\"gọi tôi\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/system-configs/HOTLINE/text").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"configValue\":\" 1900 7777 \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.configValue").value("1900 7777"));
        mvc.perform(put("/system-configs/HOTLINE/text").header(HttpHeaders.AUTHORIZATION, staffToken("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"configValue\":\"1900 8888\"}")).andExpect(status().isForbidden());
        // Tham số số không nhận chữ, tham số chữ không nhận số
        mvc.perform(put("/system-configs/OTP_MAX_ATTEMPTS/text").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"configValue\":\"5\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/system-configs/HOTLINE").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"configValue\":5}")).andExpect(status().isBadRequest());
        mvc.perform(get("/public/contact")).andExpect(status().isOk()).andExpect(jsonPath("$.data.hotline").value("1900 7777"))
                .andExpect(jsonPath("$.data.officeAddress").isNotEmpty());
    }

    @Test
    void rejectsFractionalValuesAndNonAdminAccess() throws Exception {
        String admin = staffToken("ADMIN");
        mvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configValue":1.5}
                                """)).andExpect(status().isBadRequest());
        mvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configValue":11,"maxValue":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Giá trị tham số phải nằm trong khoảng từ 1 đến 10."));
        String coordinator = staffToken("COORDINATOR");
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isForbidden());
        mvc.perform(put(URL).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configValue":3}
                                """)).andExpect(status().isForbidden());
    }

    private String staffToken(String role) {
        String username = data.unique("config-a2");
        data.staff(username, role);
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }
}
