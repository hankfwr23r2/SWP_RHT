package com.example.racehorse_transport.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.AuditService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// B6: trang nhật ký hệ thống của quản trị đọc audit_log
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class SystemLogFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private AuditService audit;
    @Autowired private AppUserRepository users;
    @Autowired private EntityManager entityManager;

    private String admin;
    private final Instant from = Instant.now().minusSeconds(3600);
    private final Instant to = Instant.now().plusSeconds(3600);

    @BeforeEach
    void setUp() {
        admin = "Bearer " + jwtUtils.generateToken("admin.dev", List.of("ADMIN"), 0);
        Long manager = users.findByUsername("manager.dev").orElseThrow().getId();
        Long coordinator = users.findByUsername("coordinator.dev").orElseThrow().getId();
        audit.record(manager, "SYSLOG_TEST_APPROVE", "syslog_test_quote", 7001L, Map.of("status", "DRAFT"), Map.of("status", "APPROVED"));
        audit.record(coordinator, "SYSLOG_TEST_DISPATCH", "syslog_test_trip", 7002L, null, Map.of("code", "VT-1"));
        audit.record(null, "SYSLOG_TEST_TIMEOUT", "syslog_test_trip", 7003L, null, Map.of("status", "TIMEOUT"));
        entityManager.flush();
    }

    @Test
    void filtersByTargetActionActorRoleAndRecordId() throws Exception {
        search(Map.of("target", "syslog_test_trip")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].actionType").value("SYSLOG_TEST_TIMEOUT"))
                .andExpect(jsonPath("$.data.content[0].actorName").doesNotExist());
        search(Map.of("actor", "MANAGER.DEV@")).andExpect(jsonPath("$.data.content[*].actionType").value(hasItem("SYSLOG_TEST_APPROVE")))
                .andExpect(jsonPath("$.data.content[*].actorName").value(everyItem(is("manager.dev"))));
        search(Map.of("role", "COORDINATOR", "target", "syslog_test_trip")).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].actorRole").value("COORDINATOR"));
        search(Map.of("key", "7001")).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].targetKey").value("7001"))
                .andExpect(jsonPath("$.data.content[0].newData").value("{\"status\":\"APPROVED\"}"));
        search(Map.of("action", "SYSLOG_TEST_DISPATCH", "size", "20", "page", "0")).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.number").value(0));
    }

    @Test
    void detailOptionsAndGuards() throws Exception {
        String body = search(Map.of("key", "7002")).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.content[0].id")).longValue();
        mvc.perform(get("/admin/system-logs/{id}", id).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionType").value("SYSLOG_TEST_DISPATCH"))
                .andExpect(jsonPath("$.data.legacy").value(false));
        mvc.perform(get("/admin/system-logs/options").header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syslog_test_trip").value(org.hamcrest.Matchers.contains("SYSLOG_TEST_DISPATCH", "SYSLOG_TEST_TIMEOUT")));

        search(Map.of("size", "30")).andExpect(status().isBadRequest());
        search(Map.of("key", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/system-logs").param("from", to.toString()).param("to", from.toString()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/system-logs").param("from", from.toString()).param("to", to.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtils.generateToken("manager.dev", List.of("MANAGER"), 0))).andExpect(status().isForbidden());
    }

    private ResultActions search(Map<String, String> filters) throws Exception {
        MockHttpServletRequestBuilder request = get("/admin/system-logs").param("from", from.toString()).param("to", to.toString())
                .header(HttpHeaders.AUTHORIZATION, admin);
        filters.forEach(request::param);
        return mvc.perform(request);
    }
}
