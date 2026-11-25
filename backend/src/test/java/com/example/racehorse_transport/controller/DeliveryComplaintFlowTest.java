package com.example.racehorse_transport.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class DeliveryComplaintFlowTest {

    private static final String PATH = "/bookings/{id}/delivery-complaints";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private Clock clock;
    @MockitoBean private CloudinaryClient storage;

    private long bookingId;
    private String customer;
    private String otherCustomer;
    private String manager;

    @BeforeEach
    void setUp() {
        var owner = testData.customer(testData.unique("complaint.owner"));
        var other = testData.customer(testData.unique("complaint.other"));
        customer = token(owner.getUser(), "CUSTOMER");
        otherCustomer = token(other.getUser(), "CUSTOMER");
        manager = token(testData.staff(testData.unique("complaint.manager"), "MANAGER").getUser(), "MANAGER");
        bookingId = testData.booking(owner, BookingType.LOCAL, BookingStatus.DELIVERED).getId();
        entityManager.flush();
        jdbc.update("""
                INSERT INTO booking_status_history (booking_id, to_status, changed_at)
                VALUES (?, 'DELIVERED', ?)
                """, bookingId, OffsetDateTime.now(clock).minus(Duration.ofDays(1)));
        when(storage.uploadFile(any(), anyString())).thenReturn("https://example.test/complaint.png");
    }

    @Test
    void customerCanConfirmOnlyTheirBookingAndCannotThenComplainInApp() throws Exception {
        json("/confirm", otherCustomer, "{\"confirmed\":true}").andExpect(status().isNotFound());
        json("/confirm", customer, "{\"confirmed\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.customerCanRespond").value(false));
        submit(photo()).andExpect(status().isConflict());
    }

    @Test
    void imageIsRequiredAndOnlyOneComplaintCanBeSubmitted() throws Exception {
        mvc.perform(multipart(PATH, bookingId).param("description", "Missing evidence")
                .header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isBadRequest());
        submit(new MockMultipartFile("photos", "fake.png", "image/png", new byte[] {1, 2, 3}))
                .andExpect(status().isBadRequest());
        submit(photo()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaint.photos[0].documentTypeCode").value("COMPLAINT_PHOTO"));
        submit(photo()).andExpect(status().isConflict());
        mvc.perform(get(PATH, bookingId).header(HttpHeaders.AUTHORIZATION, otherCustomer))
                .andExpect(status().isNotFound());
    }

    @Test
    void managerCanReplyAndResolveAndCustomerCanSeeDecision() throws Exception {
        submit(photo()).andExpect(status().isOk());
        json("/reply", customer, "{\"reply\":\"Reviewed\"}").andExpect(status().isForbidden());
        json("/reply", manager, "{\"reply\":\"Reviewed\"}").andExpect(status().isOk());
        json("/resolve", manager, "{\"decision\":\"ACCEPTED\",\"reason\":\"Reviewed evidence\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.complaint.status").value("CLOSED"));
        mvc.perform(get(PATH, bookingId).header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaint.reply").value("Reviewed"))
                .andExpect(jsonPath("$.data.complaint.decision").value("ACCEPTED"));
    }

    @Test
    void managerCanRecordLateComplaintForCompletedOrderWithoutReopeningIt() throws Exception {
        jdbc.update("UPDATE booking SET status = 'COMPLETED' WHERE id = ?", bookingId);
        jdbc.update("UPDATE booking_status_history SET changed_at = ? WHERE booking_id = ? AND to_status = 'DELIVERED'",
                OffsetDateTime.now(clock).minus(Duration.ofDays(10)), bookingId);
        entityManager.clear();
        mvc.perform(multipart(PATH + "/submit-on-behalf", bookingId).param("description", "Phone complaint")
                .header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE id = ?",
                String.class, bookingId)).isEqualTo("COMPLETED");
    }

    private ResultActions json(String path, String token, String body) throws Exception {
        return mvc.perform(post(PATH + path, bookingId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions submit(MockMultipartFile photo) throws Exception {
        return mvc.perform(multipart(PATH, bookingId).file(photo).param("description", "Delivery complaint")
                .header(HttpHeaders.AUTHORIZATION, customer));
    }

    private static MockMultipartFile photo() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("photos", "evidence.png", "image/png", bytes.toByteArray());
    }

    private String token(AppUser user, String role) {
        return "Bearer " + jwtUtils.generateToken(user.getUsername(), List.of(role));
    }
}
