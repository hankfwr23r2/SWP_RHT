package com.example.racehorse_transport.controller;

import static org.hamcrest.Matchers.contains;
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
    @MockitoBean private CloudinaryClient cloudinary;

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
                INSERT INTO horse_insurance (booking_horse_id, coverage_option, insurer_party_id, coverage_amount,
                        currency_code, policy_number, valid_from, valid_to, status, confirmed_by, confirmed_at)
                VALUES (?, 'PERSONAL', (SELECT min(party_id) FROM party_role WHERE role_type = 'INSURER'), 100000000,
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
