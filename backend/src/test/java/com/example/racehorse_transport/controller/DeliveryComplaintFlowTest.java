package com.example.racehorse_transport.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.context.transaction.TestTransaction;
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
    @Autowired private IncidentRepository incidents;
    @MockitoBean private CloudinaryClient storage;

    private long bookingId;
    private String customer;
    private String otherCustomer;
    private String manager;
    private String coordinator;

    @BeforeEach
    void setUp() {
        var owner = testData.customer(testData.unique("complaint.owner"));
        var other = testData.customer(testData.unique("complaint.other"));
        customer = token(owner.getUser(), "CUSTOMER");
        otherCustomer = token(other.getUser(), "CUSTOMER");
        manager = token(testData.staff(testData.unique("complaint.manager"), "MANAGER").getUser(), "MANAGER");
        coordinator = token(testData.staff(testData.unique("complaint.coordinator"), "COORDINATOR").getUser(), "COORDINATOR");
        bookingId = testData.booking(owner, BookingType.LOCAL, BookingStatus.DELIVERED).getId();
        entityManager.flush();
        jdbc.update("""
                INSERT INTO booking_status_history (booking_id, to_status, changed_at)
                VALUES (?, 'DELIVERED', ?)
                """, bookingId, OffsetDateTime.now(clock).minus(Duration.ofDays(1)));
        when(storage.uploadFile(any(), anyString())).thenAnswer(call -> "https://example.test/" + UUID.randomUUID() + ".png");
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
                .header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.photos").isNotEmpty());
        submit(new MockMultipartFile("photos", "fake.png", "image/png", new byte[] {1, 2, 3}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.photos").isNotEmpty());
        submit(photo()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaint.photos[0].documentTypeCode").value("COMPLAINT_PHOTO"));
        assertThat(jdbc.queryForObject("SELECT severity FROM incident WHERE booking_id = ? AND incident_type = 'COMPLAINT'",
                String.class, bookingId)).isEqualTo("LOW");
        submit(photo()).andExpect(status().isConflict());
        mvc.perform(get(PATH, bookingId).header(HttpHeaders.AUTHORIZATION, otherCustomer))
                .andExpect(status().isNotFound());
    }

    @Test
    void managerCanReplyAndResolveAndCustomerCanSeeDecision() throws Exception {
        submit(photo()).andExpect(status().isOk());
        // Mô phỏng đổi mức sau khi JPA đã nạp khiếu nại để kiểm tra phản hồi không ghi đè mức mới.
        jdbc.update("UPDATE incident SET severity = 'HIGH' WHERE booking_id = ? AND incident_type = 'COMPLAINT'", bookingId);
        json("/reply", customer, "{\"reply\":\"Reviewed\"}").andExpect(status().isForbidden());
        json("/reply", manager, "{\"reply\":\"Reviewed\"}").andExpect(status().isOk());
        json("/resolve", manager, "{\"decision\":\"ACCEPTED\",\"reason\":\"Reviewed evidence\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.complaint.status").value("CLOSED"));
        mvc.perform(get(PATH, bookingId).header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaint.reply").value("Reviewed"))
                .andExpect(jsonPath("$.data.complaint.decision").value("ACCEPTED"));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT severity FROM incident WHERE booking_id = ? AND incident_type = 'COMPLAINT'",
                String.class, bookingId)).isEqualTo("HIGH");
    }

    @Test
    void managerCanRecordAnotherComplaintOnlyAfterClosingThePreviousOne() throws Exception {
        submit(photo()).andExpect(status().isOk());
        mvc.perform(multipart(PATH + "/submit-on-behalf", bookingId).file(photo())
                .param("description", "While the first is open").header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isConflict());
        json("/resolve", manager, "{\"decision\":\"ACCEPTED\",\"reason\":\"Reviewed evidence\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.managerCanSubmit").value(true));
        submit(photo()).andExpect(status().isConflict());
        mvc.perform(multipart(PATH + "/submit-on-behalf", bookingId).file(photo())
                .param("description", "New complaint").header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.complaint.status").value("REPORTED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE booking_id = ? AND incident_type = 'COMPLAINT'",
                Integer.class, bookingId)).isEqualTo(2);
    }

    @Test
    void managerCanRecordLateComplaintForCompletedOrderWithoutReopeningIt() throws Exception {
        jdbc.update("UPDATE booking SET status = 'COMPLETED' WHERE id = ?", bookingId);
        jdbc.update("UPDATE booking_status_history SET changed_at = ? WHERE booking_id = ? AND to_status = 'DELIVERED'",
                OffsetDateTime.now(clock).minus(Duration.ofDays(10)), bookingId);
        entityManager.clear();
        mvc.perform(multipart(PATH + "/submit-on-behalf", bookingId).file(photo()).param("description", "Phone complaint")
                .header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE id = ?",
                String.class, bookingId)).isEqualTo("COMPLETED");
    }

    @Test
    void managerCanRecordOnDayFiveAfterConfirmationAndTheComplaintBlocksClosure() throws Exception {
        deliveredDaysAgo(5);
        json("/confirm", customer, "{\"confirmed\":true}").andExpect(status().isOk());
        submit(photo()).andExpect(status().isConflict());
        mvc.perform(multipart(PATH + "/submit-on-behalf", bookingId).file(photo())
                .param("description", "Customer called after confirming delivery")
                .header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaint.status").value("REPORTED"))
                .andExpect(jsonPath("$.data.complaint.photos[0].documentTypeCode").value("COMPLAINT_PHOTO"));
        assertThat(incidents.hasIncidentForBookingOtherThan(bookingId, IncidentStatus.CLOSED)).isTrue();
    }

    @Test
    void managerMustProvideAtLeastOneValidPhotoAndValidationDoesNotSaveAComplaint() throws Exception {
        deliveredDaysAgo(10);
        mvc.perform(multipart(PATH + "/submit-on-behalf", bookingId).param("description", "Missing evidence")
                .header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.photos").isNotEmpty());
        mvc.perform(multipart(PATH + "/submit-on-behalf", bookingId)
                .file(new MockMultipartFile("photos", "fake.png", "image/png", new byte[] {1, 2, 3}))
                .param("description", "Invalid evidence").header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.photos").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE booking_id = ? AND incident_type = 'COMPLAINT'",
                Integer.class, bookingId)).isZero();
    }

    @Test
    void managerCannotRecordAfterDayFourteen() throws Exception {
        deliveredDaysAgo(15);
        mvc.perform(multipart(PATH + "/submit-on-behalf", bookingId).file(photo())
                .param("description", "Late phone complaint").header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isConflict());
    }

    @Test
    void excludedHorseCannotBeSelectedForComplaint() throws Exception {
        Long excludedHorseId = jdbc.queryForObject("""
                INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status)
                VALUES (?, (SELECT MIN(id) FROM horse), 'STANDARD', 'EXCLUDED')
                RETURNING id
                """, Long.class, bookingId);
        entityManager.clear();

        mvc.perform(multipart(PATH, bookingId).file(photo()).param("description", "Excluded horse")
                .param("bookingHorseId", String.valueOf(excludedHorseId))
                .header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.bookingHorseId").isNotEmpty());
    }

    @Test
    void confirmationUnlocksSettlementClosureWhileOpenComplaintBlocksItUntilResolved() throws Exception {
        acceptZeroQuotation(bookingId);
        deliveredDaysAgo(3);

        mvc.perform(post("/bookings/{id}/settlement/complete", bookingId).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isConflict());

        json("/confirm", customer, "{\"confirmed\":true}").andExpect(status().isOk());
        mvc.perform(post("/bookings/{id}/settlement/complete", bookingId).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void resolvingComplaintAllowsClosureAndCoordinatorDoesNotSeeComplaintInOpenIncidents() throws Exception {
        acceptZeroQuotation(bookingId);
        deliveredDaysAgo(2);

        submit(photo()).andExpect(status().isOk());
        mvc.perform(get("/incidents").header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.incidentType == 'COMPLAINT')]").isEmpty());
        mvc.perform(post("/bookings/{id}/settlement/complete", bookingId).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isConflict());

        json("/resolve", manager, "{\"decision\":\"REJECTED\",\"reason\":\"No damage found\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complaint.status").value("CLOSED"));
        json("/confirm", customer, "{\"confirmed\":true}").andExpect(status().isConflict());

        deliveredDaysAgo(8);
        mvc.perform(post("/bookings/{id}/settlement/complete", bookingId).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void customerComplaintWindowAcceptsDaySevenBoundaryAndRejectsAfterwards() throws Exception {
        jdbc.update("UPDATE booking_status_history SET changed_at = ? WHERE booking_id = ? AND to_status = 'DELIVERED'",
                OffsetDateTime.now(clock).minus(Duration.ofDays(7)).plusSeconds(2), bookingId);
        entityManager.clear();
        submit(photo()).andExpect(status().isOk());

        var expiredBookingId = testData.booking(testData.customer(testData.unique("complaint.expired")),
                BookingType.LOCAL, BookingStatus.DELIVERED).getId();
        entityManager.flush();
        jdbc.update("""
                INSERT INTO booking_status_history (booking_id, to_status, changed_at)
                VALUES (?, 'DELIVERED', ?)
                """, expiredBookingId, OffsetDateTime.now(clock).minus(Duration.ofDays(7)).minusSeconds(1));
        var expiredOwner = jdbc.queryForObject("""
                SELECT u.username FROM booking b JOIN customer c ON c.user_id = b.customer_id
                JOIN app_user u ON u.id = c.user_id WHERE b.id = ?
                """, String.class, expiredBookingId);
        var expiredToken = "Bearer " + jwtUtils.generateToken(expiredOwner, List.of("CUSTOMER"));
        mvc.perform(multipart(PATH, expiredBookingId).file(photo()).param("description", "Too late")
                .header(HttpHeaders.AUTHORIZATION, expiredToken))
                .andExpect(status().isConflict());
    }

    @Test
    void simultaneousSubmissionsCreateExactlyOneComplaint() throws Exception {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> send = () -> {
                start.await();
                return submit(photo()).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(send);
            var second = pool.submit(send);
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE booking_id = ? AND incident_type = 'COMPLAINT'",
                Integer.class, bookingId)).isEqualTo(1);
    }

    private void acceptZeroQuotation(long targetBookingId) {
        jdbc.update("""
                INSERT INTO quotation (booking_id, version, price_list_id, currency_code, deposit_rate, expires_at,
                                       confirmed_pickup_date, confirmed_departure_date, estimated_delivery_date,
                                       total_amount, pass_through_amount, deposit_amount, status, sent_by, sent_at)
                VALUES (?, 1, (SELECT MIN(id) FROM price_list), 'VND', 0.3, now() + interval '1 day',
                        CURRENT_DATE + 1, CURRENT_DATE + 2, CURRENT_DATE + 3, 0, 0, 0, 'ACCEPTED',
                        (SELECT MIN(user_id) FROM staff), now())
                """, targetBookingId);
        entityManager.clear();
    }

    private void deliveredDaysAgo(int days) {
        jdbc.update("UPDATE booking_status_history SET changed_at = ? WHERE booking_id = ? AND to_status = 'DELIVERED'",
                OffsetDateTime.now(clock).minus(Duration.ofDays(days)), bookingId);
        entityManager.clear();
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
