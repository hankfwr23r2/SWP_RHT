package com.example.racehorse_transport.controller;

import static com.example.racehorse_transport.policy.BookingSubmissionPolicy.businessDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.ScheduleChangeService;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
class BookingDocumentReuseFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private Clock clock;
    @Autowired private ScheduleChangeService scheduleChanges;
    @MockitoBean private CloudinaryClient cloudinary;

    @Test
    void intakeNotifiesOnlyTheAssignedSpecialistWithTheVerificationLink() throws Exception {
        var customer = data.customer(data.unique("assigned-customer"));
        var specialist = data.staff(data.unique("assigned-specialist"), "SPECIALIST");
        var date = businessDate(clock.instant()).plusDays(2);
        var booking = booking(customer, specialist, BookingType.INTERPROVINCE, data.departure("HCM-DN", date, 4));
        booking.setStatus(BookingStatus.PENDING_INTAKE);
        booking.setSpecialist(null);
        entityManager.flush();

        mvc.perform(post("/bookings/{id}/intake", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, token("manager.dev", "MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specialistId\":" + specialist.getUserId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));

        entityManager.flush();
        assertThat(jdbc.queryForList("""
                SELECT r.user_id FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE n.booking_id = ? AND n.link_url = ? AND n.title = 'Bạn được giao phụ trách đơn'
                """, Long.class, booking.getId(), "/specialist/verification/" + booking.getId()))
                .containsExactly(specialist.getUserId());
    }

    @Test
    void warnsTheAssignedSpecialistOnceWhenAPassportExpiresBeforeDeparture() throws Exception {
        var customer = data.customer(data.unique("expiry-customer"));
        var specialist = data.staff(data.unique("expiry-specialist"), "SPECIALIST");
        var date = businessDate(clock.instant()).plusDays(2);
        var booking = booking(customer, specialist, BookingType.INTERPROVINCE, data.departure("HCM-DN", date, 4));
        long horse = profileHorse(customer);
        addHorse(booking, horse);
        when(cloudinary.uploadFile(any(), eq("documents/horse")))
                .thenReturn("https://example.test/" + data.unique("expiring-passport") + ".pdf");
        int document = uploadPassport(horse, token(customer.getUser().getUsername(), "CUSTOMER"), "passport.pdf");
        mvc.perform(post("/bookings/{id}/documents/{documentId}/approve", booking.getId(), document)
                        .header(HttpHeaders.AUTHORIZATION, token(specialist.getUser().getUsername(), "SPECIALIST")))
                .andExpect(status().isOk());
        booking.setStatus(BookingStatus.CONFIRMED);
        entityManager.flush();
        jdbc.update("UPDATE document SET expiry_date = ? WHERE id = ?", date.minusDays(1), document);
        entityManager.clear();

        scheduleChanges.remind(booking.getId());
        scheduleChanges.remind(booking.getId());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE n.booking_id = ? AND r.user_id = ? AND n.link_url = ? AND n.title = 'Giấy tờ hết hạn trước ngày chạy'
                """, Integer.class, booking.getId(), specialist.getUserId(),
                "/specialist/verification/" + booking.getId())).isEqualTo(1);
    }

    @Test
    void specialistWorkSummaryCountsOnlyAssignedOrdersAndRejectsOtherRoles() throws Exception {
        var customer = data.customer(data.unique("work-customer"));
        var specialist = data.staff(data.unique("work-specialist"), "SPECIALIST");
        var other = data.staff(data.unique("work-other"), "SPECIALIST");
        var date = businessDate(clock.instant()).plusWeeks(5);
        var departure = data.departure("HCM-PNH", date, 8);
        var underReview = booking(customer, specialist, BookingType.EXPORT, departure);
        var resubmission = booking(customer, specialist, BookingType.EXPORT, departure);
        var confirmed = booking(customer, specialist, BookingType.EXPORT, departure);
        var unrelated = booking(customer, other, BookingType.EXPORT, departure);
        resubmission.setStatus(BookingStatus.NEEDS_RESUBMISSION);
        confirmed.setStatus(BookingStatus.CONFIRMED);
        entityManager.flush();
        addHorse(underReview, profileHorse(customer));
        addHorse(confirmed, profileHorse(customer));
        addHorse(unrelated, profileHorse(customer));

        var summary = get("/specialist/work-summary");
        mvc.perform(summary.header(HttpHeaders.AUTHORIZATION, token(specialist.getUser().getUsername(), "SPECIALIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationPending").value(2))
                .andExpect(jsonPath("$.data.waitingCustomer").value(1))
                .andExpect(jsonPath("$.data.legalPending").value(1))
                .andExpect(jsonPath("$.data.insurancePending").value(2));
        mvc.perform(get("/specialist/work-queue")
                        .header(HttpHeaders.AUTHORIZATION, token(specialist.getUser().getUsername(), "SPECIALIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].booking.id", containsInAnyOrder(
                        underReview.getId().intValue(), resubmission.getId().intValue(), confirmed.getId().intValue())))
                .andExpect(jsonPath("$.data[?(@.booking.id == " + underReview.getId() + ")].horseNames[0]",
                        contains("Ngựa thử hồ sơ")));
        mvc.perform(get("/specialist/work-summary").header(HttpHeaders.AUTHORIZATION,
                        token(customer.getUser().getUsername(), "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyTheOwnerCanUploadSupportedHorseDocumentsAndPendingCountsAsSubmitted() throws Exception {
        var customer = data.customer(data.unique("profile-doc-owner"));
        var other = data.customer(data.unique("profile-doc-other"));
        String owner = token(customer.getUser().getUsername(), "CUSTOMER");
        String outsider = token(other.getUser().getUsername(), "CUSTOMER");
        long horse = profileHorse(customer);
        String url = "https://example.test/" + data.unique("profile-passport") + ".pdf";
        when(cloudinary.uploadFile(any(), eq("documents/horse"))).thenReturn(url);

        mvc.perform(get("/horses/document-types").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", containsInAnyOrder("HORSE_PASSPORT", "VACCINATION_RECORD")))
                .andExpect(jsonPath("$.data[*].ownerType", everyItem(is("HORSE"))));
        mvc.perform(get("/horses").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].color").doesNotExist())
                .andExpect(jsonPath("$.data[0].birthYear").doesNotExist())
                .andExpect(jsonPath("$.data[0].missingDocumentTypes", containsInAnyOrder("HORSE_PASSPORT", "VACCINATION_RECORD")));
        profileDocuments(horse, outsider).andExpect(status().isNotFound());
        profileUpload(horse, outsider, "HORSE_PASSPORT", pdf("passport.pdf")).andExpect(status().isNotFound());
        profileUpload(horse, owner, "HEALTH_CERT", pdf("health.pdf")).andExpect(status().isBadRequest());
        profileUpload(horse, owner, "HORSE_PASSPORT",
                new MockMultipartFile("file", "fake.pdf", "application/pdf", new byte[] {1, 2, 3}))
                .andExpect(status().isBadRequest());

        int document = uploadPassport(horse, owner, "passport.pdf");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT horse_id FROM document WHERE id = ?", Long.class, document)).isEqualTo(horse);
        assertThat(jdbc.queryForObject("SELECT document_requirement_id FROM document WHERE id = ?", Long.class, document)).isNull();
        String passport = profileType("HORSE_PASSPORT");
        profileDocuments(horse, owner).andExpect(status().isOk())
                .andExpect(jsonPath(passport + ".id", contains(document)))
                .andExpect(jsonPath(passport + ".fileUrl", contains(url)))
                .andExpect(jsonPath(passport + ".canUpload", contains(false)));
        mvc.perform(get("/horses").header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].missingDocumentTypes", contains("VACCINATION_RECORD")))
                .andExpect(jsonPath("$.data[0].documentStatuses[?(@.documentTypeCode == 'HORSE_PASSPORT')].status", contains("UPLOADED")));
        profileUpload(horse, owner, "HORSE_PASSPORT", pdf("another.pdf")).andExpect(status().isConflict());
        verify(cloudinary).uploadFile(any(), eq("documents/horse"));
    }

    @Test
    void profilePassportCanBeRejectedResubmittedApprovedAndReusedForANewBooking() throws Exception {
        var customer = data.customer(data.unique("profile-customer"));
        var specialist = data.staff(data.unique("profile-specialist"), "SPECIALIST");
        String owner = token(customer.getUser().getUsername(), "CUSTOMER");
        String staff = token(specialist.getUser().getUsername(), "SPECIALIST");
        when(cloudinary.uploadFile(any(), eq("documents/horse")))
                .thenAnswer(call -> "https://example.test/" + data.unique("profile-doc") + ".pdf");
        long horse = profileHorse(customer);
        int first = uploadPassport(horse, owner, "unreadable.pdf");
        LocalDate exportDate = businessDate(clock.instant()).plusWeeks(8);
        var domestic = booking(customer, specialist, BookingType.INTERPROVINCE,
                data.departure("HCM-DN", exportDate.minusWeeks(2), 4));
        long domesticLine = addHorse(domestic, horse);
        long domesticRequirement = passportRequirement("VN", BookingType.INTERPROVINCE);
        String firstFile = checklistDocument(first, domesticLine);

        checklist(domestic, "checklist", staff).andExpect(jsonPath(firstFile + ".status", contains("UPLOADED")))
                .andExpect(jsonPath("$.data.items[?(@.requirement.id == " + domesticRequirement + ")].satisfied", contains(false)));
        checklist(domestic, "my-checklist", owner).andExpect(jsonPath(firstFile + ".fileName", contains("unreadable.pdf")));
        mvc.perform(get("/bookings/{id}/documents", domestic.getId()).header(HttpHeaders.AUTHORIZATION, staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + first + " && @.bookingHorseId == " + domesticLine + ")].status", contains("UPLOADED")));
        mvc.perform(post("/bookings/{id}/documents/{documentId}/reject", domestic.getId(), first)
                        .header(HttpHeaders.AUTHORIZATION, staff).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Không đọc được trang nhận dạng."}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        String passport = profileType("HORSE_PASSPORT");
        profileDocuments(horse, owner).andExpect(status().isOk())
                .andExpect(jsonPath(passport + ".reviewNote", contains("Không đọc được trang nhận dạng.")))
                .andExpect(jsonPath(passport + ".status", contains("REJECTED")))
                .andExpect(jsonPath(passport + ".canUpload", contains(true)));
        mvc.perform(get("/horses/{id}", horse).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missingDocumentTypes", containsInAnyOrder("HORSE_PASSPORT", "VACCINATION_RECORD")));

        int replacement = uploadPassport(horse, owner, "readable.pdf");
        entityManager.flush();
        profileUpload(horse, owner, "HORSE_PASSPORT", pdf("duplicate.pdf")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE n.booking_id = ? AND r.user_id = ? AND n.link_url = ? AND n.title = 'Khách đã nộp giấy tờ'
                """, Integer.class, domestic.getId(), specialist.getUserId(),
                "/specialist/verification/" + domestic.getId())).isEqualTo(1);
        assertThat(replacement).isNotEqualTo(first);
        profileDocuments(horse, owner).andExpect(status().isOk())
                .andExpect(jsonPath(passport + ".id", contains(replacement)));
        mvc.perform(post("/bookings/{id}/documents/{documentId}/approve", domestic.getId(), replacement)
                        .header(HttpHeaders.AUTHORIZATION, staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        profileDocuments(horse, owner).andExpect(status().isOk())
                .andExpect(jsonPath(passport + ".canUpload", contains(false)));
        profileUpload(horse, owner, "HORSE_PASSPORT", pdf("extra.pdf")).andExpect(status().isConflict());

        var export = booking(customer, specialist, BookingType.EXPORT, data.departure("HCM-PNH", exportDate, 4));
        long exportLine = addHorse(export, horse);
        long exportRequirement = passportRequirement("KH", BookingType.EXPORT);
        for (String endpoint : List.of("checklist", "my-checklist")) {
            checklist(export, endpoint, endpoint.equals("checklist") ? staff : owner)
                    .andExpect(jsonPath("$.data.items[?(@.requirement.id == " + exportRequirement + ")].satisfied", contains(true)))
                    .andExpect(jsonPath(checklistDocument(replacement, exportLine) + ".status", contains("APPROVED")));
        }
        entityManager.flush();
        jdbc.update("UPDATE document SET expiry_date = ? WHERE id = ?", businessDate(clock.instant()).minusDays(1), replacement);
        entityManager.clear();
        profileDocuments(horse, owner).andExpect(status().isOk())
                .andExpect(jsonPath(passport + ".status", contains("EXPIRED")))
                .andExpect(jsonPath(passport + ".canUpload", contains(true)));
        mvc.perform(get("/horses/{id}", horse).header(HttpHeaders.AUTHORIZATION, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missingDocumentTypes", containsInAnyOrder("HORSE_PASSPORT", "VACCINATION_RECORD")));
    }

    @Test
    void profileDocumentsAreOptionalForALocalBookingAndOnlyReviewableForAHorseInThatBooking() throws Exception {
        var customer = data.customer(data.unique("profile-local-customer"));
        var specialist = data.staff(data.unique("profile-local-specialist"), "SPECIALIST");
        String owner = token(customer.getUser().getUsername(), "CUSTOMER");
        String staff = token(specialist.getUser().getUsername(), "SPECIALIST");
        long horse = profileHorse(customer);
        when(cloudinary.uploadFile(any(), eq("documents/horse")))
                .thenReturn("https://example.test/" + data.unique("local-passport") + ".pdf");
        int document = uploadPassport(horse, owner, "local.pdf");
        var local = data.booking(customer, BookingType.LOCAL, BookingStatus.UNDER_REVIEW);
        local.setSpecialist(specialist);
        data.bookingAddress(local, AddressRole.PICKUP);
        data.bookingAddress(local, AddressRole.DELIVERY);
        entityManager.flush();
        mvc.perform(post("/bookings/{id}/documents/{documentId}/approve", local.getId(), document)
                        .header(HttpHeaders.AUTHORIZATION, staff))
                .andExpect(status().isNotFound());
        int bookingHorse = Math.toIntExact(addHorse(local, horse));

        checklist(local, "checklist", staff)
                .andExpect(jsonPath("$.data.items[0].profileDocumentType.code").value("HORSE_PASSPORT"))
                .andExpect(jsonPath("$.data.items[0].requiredForQuoting").value(false))
                .andExpect(jsonPath("$.data.items[0].requiredBeforePickup").value(false))
                .andExpect(jsonPath("$.data.items[0].bookingHorseId").value(bookingHorse));
        mvc.perform(post("/bookings/{id}/documents/{documentId}/approve", local.getId(), document)
                        .header(HttpHeaders.AUTHORIZATION, staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void exportReusesTheHorsePassportApprovedForAnInterprovinceBooking() throws Exception {
        var customer = data.customer(data.unique("f9-customer"));
        var specialist = data.staff(data.unique("f9-specialist"), "SPECIALIST");
        String ownerToken = token(customer.getUser().getUsername(), "CUSTOMER");
        String staffToken = token(specialist.getUser().getUsername(), "SPECIALIST");
        LocalDate exportDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).plusWeeks(8);
        var domestic = booking(customer, specialist, BookingType.INTERPROVINCE,
                data.departure("HCM-DN", exportDate.minusWeeks(2), 4));
        var export = booking(customer, specialist, BookingType.EXPORT, data.departure("HCM-PNH", exportDate, 4));

        long horseId = jdbc.queryForObject("""
                INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                VALUES (?, 'Ngựa thử 2.F9', 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE') RETURNING id
                """, Long.class, customer.getUser().getId());
        long domesticLine = addHorse(domestic, horseId);
        long exportLine = addHorse(export, horseId);
        long domesticRequirement = passportRequirement("VN", BookingType.INTERPROVINCE);
        long exportRequirement = passportRequirement("KH", BookingType.EXPORT);
        String exportSatisfied = "$.data.items[?(@.requirement.id == " + exportRequirement + ")].satisfied";
        confirmRemainingQuoteEvidence(exportLine, exportDate, customer.getUser().getId(), horseId, specialist.getUserId());

        var file = new MockMultipartFile("file", "passport.pdf", "application/pdf",
                new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '7'});
        when(cloudinary.uploadFile(any(), eq("documents/horse")))
                .thenReturn("https://example.test/" + data.unique("f9-passport") + ".pdf");
        var upload = mvc.perform(multipart("/bookings/{id}/documents/upload", domestic.getId()).file(file)
                        .param("requirementId", String.valueOf(domesticRequirement))
                        .param("bookingHorseId", String.valueOf(domesticLine)).param("expiryDate", exportDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UPLOADED"));
        long passport = ((Number) JsonPath.read(upload.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
        String reusedFile = "$.data.documents[?(@.id == " + passport + " && @.requirementId == " + domesticRequirement
                + " && @.bookingHorseId == " + exportLine + ")].fileName";

        checklist(export, "my-checklist", ownerToken).andExpect(jsonPath(exportSatisfied, contains(false)))
                .andExpect(jsonPath(reusedFile).isEmpty());
        mvc.perform(post("/bookings/{id}/documents/{documentId}/approve", domestic.getId(), passport)
                        .header(HttpHeaders.AUTHORIZATION, staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));

        checklist(export, "my-checklist", ownerToken).andExpect(jsonPath(exportSatisfied, contains(true)))
                .andExpect(jsonPath(reusedFile, contains("passport.pdf")));
        checklist(export, "checklist", staffToken).andExpect(jsonPath(exportSatisfied, contains(true)))
                .andExpect(jsonPath(reusedFile, contains("passport.pdf")))
                .andExpect(jsonPath("$.data.documents[?(@.fileName == 'vaccines.pdf' && @.bookingHorseId == "
                        + exportLine + ")].status", contains("APPROVED")));
        mvc.perform(multipart("/bookings/{id}/documents/upload", export.getId()).file(file)
                        .param("requirementId", String.valueOf(exportRequirement)).param("bookingHorseId", String.valueOf(exportLine))
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Giấy tờ của yêu cầu này đang chờ duyệt hoặc còn hiệu lực."));
        verify(cloudinary).uploadFile(any(), eq("documents/horse"));

        mvc.perform(post("/bookings/{id}/start-quoting", export.getId()).header(HttpHeaders.AUTHORIZATION, staffToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("QUOTING"));
    }

    private Booking booking(Customer customer, Staff specialist, BookingType type, Departure departure) {
        var booking = data.booking(customer, type, BookingStatus.UNDER_REVIEW);
        booking.setDeparture(departure);
        booking.setSpecialist(specialist);
        booking.setRequestedPickupDate(departure.getDepartureDate().minusDays(1));
        departure.setBookedStallCount(1);
        var pickup = data.bookingAddress(booking, AddressRole.PICKUP);
        pickup.setProvince(entityManager.getReference(Province.class, "VN-79"));
        pickup.setLatitude(new BigDecimal("10.800000"));
        pickup.setLongitude(new BigDecimal("106.740000"));
        var delivery = data.bookingAddress(booking, AddressRole.DELIVERY);
        if (type == BookingType.EXPORT) {
            delivery.setProvince(entityManager.getReference(Province.class, "KH-12"));
            delivery.setCountry(entityManager.getReference(Country.class, "KH"));
            delivery.setLatitude(new BigDecimal("11.556400"));
            delivery.setLongitude(new BigDecimal("104.928200"));
        }
        entityManager.flush();
        return booking;
    }

    private long profileHorse(Customer customer) {
        entityManager.flush();
        return jdbc.queryForObject("""
                INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                VALUES (?, 'Ngựa thử hồ sơ', 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE') RETURNING id
                """, Long.class, customer.getUser().getId());
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '7'});
    }

    private int uploadPassport(long horse, String owner, String file) throws Exception {
        var upload = profileUpload(horse, owner, "HORSE_PASSPORT", pdf(file))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andExpect(jsonPath("$.data.documentTypeCode").value("HORSE_PASSPORT"))
                .andExpect(jsonPath("$.data.uploadedAt").exists());
        return JsonPath.read(upload.andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    private ResultActions profileUpload(long horse, String token, String type, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/horses/{id}/documents", horse).file(file).param("documentTypeCode", type)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions profileDocuments(long horse, String token) throws Exception {
        return mvc.perform(get("/horses/{id}/documents", horse).header(HttpHeaders.AUTHORIZATION, token));
    }

    private static String profileType(String type) {
        return "$.data[?(@.documentTypeCode == '" + type + "')]";
    }

    private static String checklistDocument(int document, long bookingHorse) {
        return "$.data.documents[?(@.id == " + document + " && @.bookingHorseId == " + bookingHorse + ")]";
    }

    private long addHorse(Booking booking, long horseId) {
        return jdbc.queryForObject("""
                INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status, delivery_address_id)
                SELECT booking_id, ?, 'STANDARD', 'ACTIVE', id FROM booking_address
                WHERE booking_id = ? AND address_type = 'DELIVERY' RETURNING id
                """, Long.class, horseId, booking.getId());
    }

    private long passportRequirement(String country, BookingType type) {
        return jdbc.queryForObject("""
                SELECT dr.id FROM document_requirement dr JOIN country_rule cr ON cr.id = dr.country_rule_id
                JOIN document_type dt ON dt.id = dr.document_type_id
                WHERE cr.country_code = ? AND cr.direction = ? AND dt.code = 'HORSE_PASSPORT' AND dr.gate_location_id IS NULL
                """, Long.class, country, type.name());
    }

    private void confirmRemainingQuoteEvidence(long line, LocalDate departure, long customer, long horse, long specialist) {
        jdbc.update("""
                INSERT INTO horse_insurance (booking_horse_id, coverage_option, insurer_name, coverage_amount,
                        currency_code, policy_number, valid_from, valid_to, status, confirmed_by, confirmed_at)
                VALUES (?, 'PERSONAL', 'Hãng bảo hiểm mẫu', 100000000,
                        'VND', 'F9-POLICY', ?, ?, 'CONFIRMED', ?, now())
                """, line, departure.minusDays(1), departure, specialist);
        jdbc.update("""
                INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, reviewed_by, reviewed_at, horse_id)
                SELECT id, ?, 'vaccines.pdf', ?, 'APPROVED', ?, now(), ? FROM document_type WHERE code = 'VACCINATION_RECORD'
                """, "https://example.test/" + data.unique("f9-vaccines") + ".pdf", customer, specialist, horse);
    }

    private ResultActions checklist(Booking booking, String endpoint, String token) throws Exception {
        return mvc.perform(get("/bookings/{id}/documents/{endpoint}", booking.getId(), endpoint)
                        .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
    }

    private String token(String username, String role) {
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }
}
