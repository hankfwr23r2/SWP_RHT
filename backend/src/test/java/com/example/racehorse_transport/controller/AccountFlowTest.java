package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.MockTurnstileVerification;
import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@MockTurnstileVerification
@Transactional
class AccountFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private LocationRepository locations;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void adminCreatesDriverAndTheDriverCanSignIn() throws Exception {
        String token = staffToken("ADMIN");
        long hub = locations.findByCode("HUB-HCM").orElseThrow().getId();
        String payload = createBody("DRIVER", hub);
        String response = mvc.perform(post("/staff-accounts").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.roleCode").value("DRIVER"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.data.userId")).longValue();
        assertThat(id).isNotEqualTo(99999L);
        mvc.perform(get("/staff-accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(put("/staff-accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"staffCode":" flow-a2 ","fullName":"Updated Driver","phone":"0981234567",
                                 "hireDate":"2026-11-23","homeHubLocationId":%d,"roleCode":"ADMIN"}
                                """.formatted(hub)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roleCode").value("DRIVER"))
                .andExpect(jsonPath("$.data.staffCode").value("FLOW-A2"))
                .andExpect(jsonPath("$.data.fullName").value("Updated Driver"));
        mvc.perform(post("/auth/staff/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"a2-flow-driver","password":"test-password"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isString());
        mvc.perform(post("/staff-accounts").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
    }

    @Test
    void restrictsManagementAndRefusesCustomerRole() throws Exception {
        mvc.perform(get("/staff-accounts").header(HttpHeaders.AUTHORIZATION, staffToken("COORDINATOR")))
                .andExpect(status().isForbidden());
        long hub = locations.findByCode("HUB-HCM").orElseThrow().getId();
        mvc.perform(post("/staff-accounts").header(HttpHeaders.AUTHORIZATION, staffToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("CUSTOMER", hub)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidStaffCodesOnCreateAndUpdate() throws Exception {
        String token = staffToken("ADMIN");
        long hub = locations.findByCode("HUB-HCM").orElseThrow().getId();
        String payload = createBody("DRIVER", hub);
        mvc.perform(post("/staff-accounts").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload.replace("FLOW-A2", "FLOW/A2")))
                .andExpect(status().isBadRequest());
        String response = mvc.perform(post("/staff-accounts").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.data.userId")).longValue();
        mvc.perform(put("/staff-accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"staffCode":"FLOW A2","fullName":"Driver Test","phone":"0981234567",
                                 "hireDate":"2026-11-23","homeHubLocationId":%d}
                                """.formatted(hub)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminLocksAndUnlocksStaffWithAuditAndLockedTokenStopsWorking() throws Exception {
        String admin = data.unique("lock-admin");
        long adminId = data.staff(admin, "ADMIN").getUserId();
        String token = "Bearer " + jwt.generateToken(admin, List.of("ADMIN"), 0);
        String worker = data.unique("lock-worker");
        long id = data.staff(worker, "COORDINATOR").getUserId();
        String workerToken = "Bearer " + jwt.generateToken(worker, List.of("COORDINATOR"), 0);
        String reason = "{\"reason\":\"Nghỉ việc\"}";

        mvc.perform(post("/staff-accounts/{id}/lock", id).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/staff-accounts/{id}/lock", id).header(HttpHeaders.AUTHORIZATION, workerToken)
                .contentType(MediaType.APPLICATION_JSON).content(reason)).andExpect(status().isForbidden());
        mvc.perform(post("/staff-accounts/{id}/lock", adminId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(reason)).andExpect(status().isBadRequest());

        mvc.perform(post("/staff-accounts/{id}/lock", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accountStatus").value("LOCKED"));
        mvc.perform(get("/dispatch/vehicles").header(HttpHeaders.AUTHORIZATION, workerToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/staff-accounts/{id}/lock", id).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(reason)).andExpect(status().isConflict());
        mvc.perform(post("/staff-accounts/{id}/unlock", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
        assertThat(jdbc.queryForList("select action from audit_log where target_table = 'app_user' and target_id = ? order by id",
                String.class, id)).containsExactly("LOCK_STAFF_ACCOUNT", "UNLOCK_STAFF_ACCOUNT");
    }

    private String createBody(String role, long hub) {
        return """
                {"username":"a2-flow-driver","email":"a2-flow-driver@example.com","password":"test-password",
                 "roleCode":"%s","staffCode":"FLOW-A2","fullName":"Driver Test","phone":"0981234567",
                 "hireDate":"2026-11-23","homeHubLocationId":%d,"userId":99999,"accountStatus":"LOCKED"}
                """.formatted(role, hub);
    }

    private String staffToken(String role) {
        String username = data.unique("account-a2");
        data.staff(username, role);
        return "Bearer " + jwt.generateToken(username, List.of(role), 0);
    }
}
