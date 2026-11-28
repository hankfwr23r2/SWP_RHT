package com.example.racehorse_transport.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.NotificationService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// B6: chuông thông báo, mỗi người chỉ thấy và sửa thông báo gửi cho mình
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class NotificationInboxFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private AppUserRepository users;
    @Autowired private NotificationService notifications;
    @Autowired private EntityManager entityManager;

    private String customer;
    private String manager;
    private long notificationId;

    @BeforeEach
    void setUp() throws Exception {
        customer = "Bearer " + jwtUtils.generateToken("customer1.dev", List.of("CUSTOMER"), 0);
        manager = "Bearer " + jwtUtils.generateToken("manager.dev", List.of("MANAGER"), 0);
        Long customerId = users.findByUsername("customer1.dev").orElseThrow().getId();
        Long managerId = users.findByUsername("manager.dev").orElseThrow().getId();
        notifications.notify(List.of(customerId, managerId), null, "Tin thử chuông", "Nội dung tin thử", "/orders/1");
        notifications.notify(List.of(customerId), null, "Tin thứ hai", "Chỉ khách nhận", null);
        entityManager.flush();
        List<Number> ids = JsonPath.read(mine(customer).andReturn().getResponse().getContentAsString(), "$.data[?(@.title == 'Tin thử chuông')].id");
        notificationId = ids.get(0).longValue();
    }

    @Test
    void readAndDeleteOnlyAffectTheCurrentUser() throws Exception {
        mine(customer).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Tin thứ hai"))
                .andExpect(jsonPath("$.data[?(@.title == 'Tin thử chuông')].read").value(hasItem(false)))
                .andExpect(jsonPath("$.data[?(@.title == 'Tin thử chuông')].linkUrl").value(hasItem("/orders/1")));

        mvc.perform(post("/notifications/{id}/read", notificationId).header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isOk());
        mine(customer).andExpect(jsonPath("$.data[?(@.title == 'Tin thử chuông')].read").value(hasItem(true)));
        mine(manager).andExpect(jsonPath("$.data[?(@.title == 'Tin thử chuông')].read").value(hasItem(false)));

        mvc.perform(delete("/notifications/{id}", notificationId).header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isOk());
        mine(customer).andExpect(jsonPath("$.data[*].title").value(not(hasItem("Tin thử chuông"))));
        mine(manager).andExpect(jsonPath("$.data[*].title").value(hasItem("Tin thử chuông")));
    }

    @Test
    void cannotTouchANotificationSentToSomeoneElse() throws Exception {
        long customerOnly = ((Number) JsonPath.read(mine(customer).andReturn().getResponse().getContentAsString(), "$.data[0].id")).longValue();
        mvc.perform(post("/notifications/{id}/read", customerOnly).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isNotFound());
        mvc.perform(delete("/notifications/{id}", customerOnly).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isNotFound());
        mvc.perform(get("/notifications/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void readAllAndDeleteAllClearOnlyMyInbox() throws Exception {
        mvc.perform(post("/notifications/read-all").header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isOk());
        mine(customer).andExpect(jsonPath("$.data[*].read").value(not(hasItem(false))));
        mine(manager).andExpect(jsonPath("$.data[?(@.title == 'Tin thử chuông')].read").value(hasItem(false)));

        mvc.perform(delete("/notifications/mine").header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isOk());
        mine(customer).andExpect(jsonPath("$.data.length()").value(0));
        mine(manager).andExpect(jsonPath("$.data[*].title").value(hasItem("Tin thử chuông")));
    }

    private ResultActions mine(String token) throws Exception {
        return mvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, token));
    }
}
