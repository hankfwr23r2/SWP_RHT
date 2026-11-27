package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.HealthCheckTaskService;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class HealthCheckTaskFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private Clock clock;
    @Autowired private HealthCheckTaskService tasks;

    private Staff coordinator;
    private Staff escort;
    private Staff otherEscort;
    private long horse;
    private long stay;

    @BeforeEach
    void setUp() {
        coordinator = data.staff(data.unique("check.coordinator"), "COORDINATOR");
        escort = data.staff(data.unique("check.escort"), "ESCORT");
        otherEscort = data.staff(data.unique("check.other"), "ESCORT");
        horse = jdbc.queryForObject("""
                SELECT bh.id FROM booking_horse bh JOIN booking b ON b.id = bh.booking_id
                WHERE b.code = 'DEV-DIRECT-001'
                """, Long.class);
        entityManager.flush();
        stay = jdbc.queryForObject("""
                INSERT INTO horse_stay (booking_horse_id, location_id, stay_type, planned_start_at, planned_end_at, actual_start_at)
                VALUES (?, ?, 'ASSEMBLY', now(), now() + interval '1 hour', now()) RETURNING id
                """, Long.class, horse, escort.getHomeHubLocation().getId());
    }

    @Test
    void assignmentGrantsExactStayAccessAndLogCompletesTheTask() throws Exception {
        mvc.perform(get("/health-check-tasks/assignees").header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR"))
                .param("bookingHorseId", Long.toString(horse)).param("horseStayId", Long.toString(stay)))
                .andExpect(status().isOk());
        long task = assign();
        mvc.perform(get("/health-check-tasks/mine").header(HttpHeaders.AUTHORIZATION, token(escort, "ESCORT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(task));
        act(task, "accept", otherEscort, "ESCORT", null).andExpect(status().isForbidden());
        log(otherEscort).andExpect(status().isForbidden());
        act(task, "accept", escort, "ESCORT", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        log(escort).andExpect(status().isCreated());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM health_check_task WHERE id = ?", String.class, task)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT health_log_id FROM health_check_task WHERE id = ?", Long.class, task)).isNotNull();
    }

    @Test
    void cancellationRequiresAnOwnerAndReasonAndRevokesTaskAccess() throws Exception {
        long task = assign();
        Staff other = data.staff(data.unique("check.other.coordinator"), "COORDINATOR");
        String reason = """
                {"reason":"Reassigned"}
                """;
        act(task, "cancel", other, "COORDINATOR", reason).andExpect(status().isForbidden());
        act(task, "cancel", coordinator, "COORDINATOR", """
                {"reason":" "}
                """).andExpect(status().isBadRequest());
        act(task, "cancel", coordinator, "COORDINATOR", reason).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        log(escort).andExpect(status().isForbidden());
    }

    @Test
    void overdueTaskSendsExactlyOneNotification() throws Exception {
        long task = assign();
        entityManager.flush();
        jdbc.update("UPDATE health_check_task SET due_at = now() - interval '1 minute' WHERE id = ?", task);
        entityManager.clear();
        mvc.perform(get("/health-check-tasks/mine").header(HttpHeaders.AUTHORIZATION, token(escort, "ESCORT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].overdue").value(true));
        tasks.notifyOverdue();
        entityManager.flush();
        tasks.notifyOverdue();
        entityManager.flush();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification_recipient r JOIN notification n ON n.id = r.notification_id
                WHERE r.user_id = ? AND n.message LIKE ?
                """, Integer.class, coordinator.getUserId(), "%(#" + task + ")")).isEqualTo(1);
    }

    @Test
    void driverCannotBeAssignedAndWrongHorseIsRejected() throws Exception {
        Staff driver = data.staff(data.unique("check.driver"), "DRIVER");
        assignRequest(horse, driver).andExpect(status().isBadRequest());
        assignRequest(Long.MAX_VALUE, escort).andExpect(status().isConflict());
    }

    @Test
    void beforeDepartureCheckRequiresATripTarget() throws Exception {
        assignRequest(horse, escort, "BEFORE_DEPARTURE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.checkType").value("Giao khám trước khi khởi hành từ danh sách ngựa của lượt xe trục."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM health_check_task WHERE horse_stay_id = ?", Long.class, stay)).isZero();
    }

    private long assign() throws Exception {
        Number id = JsonPath.read(assignRequest(horse, escort).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private ResultActions assignRequest(long horseId, Staff assignee) throws Exception {
        return assignRequest(horseId, assignee, "ROUTINE");
    }

    private ResultActions assignRequest(long horseId, Staff assignee, String checkType) throws Exception {
        return mvc.perform(post("/health-check-tasks").header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookingHorseId":%d,"horseStayId":%d,"assignedTo":%d,"checkType":"%s","dueAt":"%s"}
                        """.formatted(horseId, stay, assignee.getUserId(), checkType, clock.instant().plusSeconds(3600))));
    }

    private ResultActions log(Staff recorder) throws Exception {
        return mvc.perform(post("/health-logs").header(HttpHeaders.AUTHORIZATION, token(recorder, "ESCORT"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookingHorseId":%d,"horseStayId":%d,"healthStatus":"NORMAL"}
                        """.formatted(horse, stay)));
    }

    private ResultActions act(long task, String action, Staff actor, String role, String body) throws Exception {
        var request = post("/health-check-tasks/{id}/{action}", task, action).header(HttpHeaders.AUTHORIZATION, token(actor, role));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private String token(Staff actor, String role) {
        return "Bearer " + jwt.generateToken(actor.getUser().getUsername(), List.of(role));
    }
}
